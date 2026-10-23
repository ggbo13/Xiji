package com.xiji.habits.ui.home

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.xiji.habits.ui.theme.XijiMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 待完成列表在 LazyColumn 里的 key 前缀，与今日页保持一致 */
internal const val PENDING_KEY_PREFIX = "p_"

internal fun pendingKey(id: Long?): String = "$PENDING_KEY_PREFIX$id"

/**
 * 把拖动后的「待完成」顺序填回完整列表。
 *
 * 数据库里的 sortOrder 是整份习惯列表共用的，只提交待完成那一段会让已完成习惯
 * 的序号错位，所以这里按原来的槽位替换：已完成/暂停的项位置不动。
 */
internal fun splicePendingOrder(
    allIds: List<Long>,
    isPending: List<Boolean>,
    newPendingIds: List<Long>
): List<Long> {
    if (allIds.size != isPending.size) return allIds
    val slots = allIds.indices.filter { isPending[it] }
    if (slots.size != newPendingIds.size) return allIds
    if (slots.isEmpty()) return allIds
    val result = allIds.toMutableList()
    slots.forEachIndexed { i, slot -> result[slot] = newPendingIds[i] }
    return result
}

/**
 * 今日页「排序」模式的拖拽状态机。
 *
 * 做法：拖动时不让 LazyColumn 自己搬东西，而是
 * 1. 卡片本身的位移由 [translation] 直接跟随手指（graphicsLayer.translationY）；
 * 2. 按相邻卡片的中心与高度判定换位，其余卡片用 animateItem 平滑让位；
 * 3. 位移直接读取当前布局坐标，使换位和自动滚动与绘制使用同一帧的数据；
 * 4. 松手后保留屏幕位置，再用短弹簧回到新槽位，等待数据库确认保存。
 */
@Stable
class ReorderState(
    private val listState: LazyListState,
    private val haptics: HapticFeedback,
    private val scope: CoroutineScope,
    private val reduceMotion: Boolean,
    private val edgePx: Float,
    private val onCommit: (List<Long>) -> Unit
) {
    /** 当前的待完成顺序（拖动中即本地顺序，松手后不回退，避免闪一下） */
    var orderedIds by mutableStateOf<List<Long>>(emptyList())
        private set

    var draggingId by mutableStateOf<Long?>(null)
        private set

    /** 松手回弹仍只作用于原卡片，不能把位移套到整份列表上。 */
    var settlingId by mutableStateOf<Long?>(null)
        private set

    private var settleProgress by mutableFloatStateOf(1f)
    private var settleStartTop = 0f

    /** 在同一帧读取布局坐标，避免换位后等待异步回调才修正卡片位置。 */
    val translation: Float
        get() {
            val id = draggingId ?: settlingId ?: return 0f
            val visible = listState.layoutInfo.visibleItemsInfo
            val item = visible.firstOrNull { it.key == pendingKey(id) } ?: return 0f
            if (draggingId == null) return (settleStartTop - item.offset) * (1f - settleProgress)
            val pending = visible.filter { (it.key as? String)?.startsWith(PENDING_KEY_PREFIX) == true }
            // 换位前后使用当前布局的整个分组边界，不能混用新顺序和旧坐标。
            val first = pending.minByOrNull { it.offset }
                ?.takeIf { pending.any { it.key == pendingKey(orderedIds.firstOrNull()) } }
            val last = pending.maxByOrNull { it.offset + it.size }
                ?.takeIf { pending.any { it.key == pendingKey(orderedIds.lastOrNull()) } }
            val minTop = first?.offset?.toFloat() ?: Float.NEGATIVE_INFINITY
            val maxTop = last?.let { (it.offset + it.size - item.size).toFloat() } ?: Float.POSITIVE_INFINITY
            val top = (startOffset + totalDrag).coerceIn(minTop, maxTop)
            return top - item.offset
        }

    private var startOffset = 0f
    private var totalDrag by mutableFloatStateOf(0f)
    private var orderAtGrab: List<Long> = emptyList()
    private var pendingCommit: List<Long>? = null
    private var settleJob: Job? = null
    private var autoScrollJob: Job? = null

    /** 同步外部数据；排序模式下本地顺序优先，只补齐新增/移除的项 */
    fun sync(rows: List<HabitRowUi>, reorderMode: Boolean) {
        val incoming = rows.map { it.habit.id }
        if (draggingId != null) {
            if (!reorderMode || draggingId !in incoming) endDrag(cancelled = true) else return
        }
        val awaiting = pendingCommit
        if (awaiting != null) {
            if (incoming == awaiting || incoming.toSet() != awaiting.toSet()) pendingCommit = null
            else if (!reorderMode) return
        }
        if (!reorderMode) {
            if (orderedIds != incoming) orderedIds = incoming
            return
        }
        val present = incoming.toHashSet()
        val known = orderedIds.toHashSet()
        val merged = orderedIds.filter { it in present } + incoming.filter { it !in known }
        if (merged != orderedIds) orderedIds = merged
    }

    /** 按本地顺序重排列表；数据还没对齐时退回仓库顺序 */
    fun ordered(rows: List<HabitRowUi>): List<HabitRowUi> {
        if (orderedIds.isEmpty()) return rows
        val byId = rows.associateBy { it.habit.id }
        val result = ArrayList<HabitRowUi>(rows.size)
        orderedIds.forEach { id -> byId[id]?.let(result::add) }
        return if (result.size == rows.size) result else rows
    }

    /** 给拖拽把手用的手势修饰符 */
    fun dragModifier(habitId: Long, enabled: Boolean): Modifier = Modifier.pointerInput(habitId, enabled) {
        if (!enabled) return@pointerInput
        detectDragGestures(
            onDragStart = { beginDrag(habitId) },
            onDragEnd = { endDrag() },
            onDragCancel = { endDrag(cancelled = true) }
        ) { change, drag ->
            change.consume()
            totalDrag += drag.y
            swapIfNeeded()
        }
    }

    /**
     * 不经手指的移动：给 TalkBack 的「上移/下移」自定义操作使用。
     * 只拖动排序对手部不便或看不见屏幕的人不可用，必须有一个等价的替代入口。
     */
    fun move(habitId: Long, delta: Int) {
        val from = orderedIds.indexOf(habitId)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, orderedIds.lastIndex)
        if (to == from) return
        orderedIds = orderedIds.toMutableList().apply { add(to, removeAt(from)) }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        pendingCommit = orderedIds
        onCommit(orderedIds)
    }

    /** 交换或自动滚动改变布局后，继续检查相邻卡片是否需要换位。 */
    fun onLayoutChanged() {
        if (draggingId != null) swapIfNeeded()
    }

    private fun beginDrag(id: Long) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == pendingKey(id) } ?: return
        val visualTop = info.offset + if (settlingId == id) translation else 0f
        settleJob?.cancel()
        settlingId = null
        autoScrollJob?.cancel()
        startOffset = visualTop
        totalDrag = 0f
        settleProgress = 1f
        orderAtGrab = orderedIds
        draggingId = id
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        autoScrollJob = scope.launch { autoScrollLoop(id) }
    }

    private fun swapIfNeeded() {
        val id = draggingId ?: return
        val visible = listState.layoutInfo.visibleItemsInfo
        val self = visible.firstOrNull { it.key == pendingKey(id) } ?: return
        // 越过相邻卡片的中心才换位；换位后必须真正向回拖动才能反向换位。
        val center = self.offset + translation + self.size / 2f
        val from = orderedIds.indexOf(id)
        if (from < 0) return
        val direction = if (translation > 0f) 1 else if (translation < 0f) -1 else return
        val to = from + direction
        val targetId = orderedIds.getOrNull(to) ?: return
        val target = visible.firstOrNull { it.key == pendingKey(targetId) } ?: return
        // 本地顺序已变但布局还没更新时，不能用旧坐标再次换位。
        if ((target.index - self.index) * direction <= 0) return
        val targetCenter = target.offset + target.size / 2f
        // 高卡片到达相邻槽位时，其中心可能仍超出矮卡片中心，按高度差补偿阈值。
        val crossingCenter = center + direction * ((self.size - target.size).coerceAtLeast(0) / 2f)
        if (direction > 0 && crossingCenter < targetCenter || direction < 0 && crossingCenter > targetCenter) return
        if (self.index == listState.firstVisibleItemIndex || target.index == listState.firstVisibleItemIndex) {
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
        orderedIds = orderedIds.toMutableList().apply { add(to, removeAt(from)) }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    private fun endDrag(cancelled: Boolean = false) {
        val id = draggingId ?: return
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == pendingKey(id) }
        settleStartTop = (item?.offset ?: 0) + translation
        settleProgress = 0f
        settlingId = if (!cancelled && !reduceMotion) id else null
        draggingId = null
        autoScrollJob?.cancel()
        autoScrollJob = null
        if (cancelled) orderedIds = orderAtGrab
        else if (orderedIds != orderAtGrab) {
            pendingCommit = orderedIds
            onCommit(orderedIds)
        }
        if (settlingId == null) {
            settleProgress = 1f
            return
        }
        settleJob?.cancel()
        settleJob = scope.launch {
            // 保留松手时的屏幕位置，目标槽位随布局更新，避免刚换位就松手时多跳一格。
            animate(0f, 1f, animationSpec = XijiMotion.settle(false)) { value, _ ->
                settleProgress = value.coerceIn(0f, 1f)
            }
            settleProgress = 1f
            settlingId = null
        }
    }

    /** 手指靠近列表上下边缘时缓慢滚动，避免长列表拖不到头 */
    private suspend fun autoScrollLoop(id: Long) {
        val maxStep = edgePx * 0.22f
        var lastFrame = withFrameNanos { it }
        while (draggingId == id) {
            val frame = withFrameNanos { it }
            // 按实际帧间隔换算速度，在高刷新率屏幕上保持相同的滚动速度。
            val frameScale = ((frame - lastFrame) / 16_000_000f).coerceIn(0f, 2f)
            lastFrame = frame
            val layout = listState.layoutInfo
            val self = layout.visibleItemsInfo.firstOrNull { it.key == pendingKey(id) }
            val viewport = layout.viewportSize.height
            if (self != null && viewport > 0) {
                val top = self.offset + translation
                val bottom = top + self.size
                val overflow = when {
                    top < edgePx && orderedIds.firstOrNull() != id -> top - edgePx
                    bottom > viewport - edgePx && orderedIds.lastOrNull() != id -> bottom - (viewport - edgePx)
                    else -> 0f
                }
                if (overflow != 0f) {
                    // 越靠边滚得越快，按帧间隔换算位移并限制补偿，避免突然跳动。
                    val step = (overflow / edgePx).coerceIn(-1f, 1f) * maxStep
                    listState.scrollBy(step * frameScale)
                    swapIfNeeded()
                }
            }
        }
    }
}

/** 拖拽把手的触控尺寸，跟随手势一起暴露给 UI */
internal val DragHandleSize = 44.dp

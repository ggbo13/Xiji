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
 * 2. 越过分隔线时立即交换 [orderedIds] 里的位置，其余卡片用 animateItem 平滑让位；
 * 3. 交换后卡片在布局里的真实 offset 会突变，于是用 layout 变化回调把
 *    translation 重新算成「手指位置 − 新 offset」，视觉上保持连续；
 * 4. 松手后用一条短弹簧把残余位移吸到 0，结束在正确的槽位。
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

    /** 被拖起的卡片相对自己布局位置的位移 */
    var translation by mutableFloatStateOf(0f)
        private set

    private var startOffset = 0f
    private var totalDrag = 0f
    private var orderAtGrab: List<Long> = emptyList()
    private var settleJob: Job? = null
    private var autoScrollJob: Job? = null

    /** 同步外部数据；排序模式下本地顺序优先，只补齐新增/移除的项 */
    fun sync(rows: List<HabitRowUi>, reorderMode: Boolean) {
        if (draggingId != null) return
        val incoming = rows.map { it.habit.id }
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
            onDragCancel = { endDrag() }
        ) { change, drag ->
            change.consume()
            totalDrag += drag.y
            applyTranslation()
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
        onCommit(orderedIds)
    }

    /** 布局变化（交换、自动滚动）后重新对齐位移 */
    fun onLayoutChanged() {
        if (draggingId != null) applyTranslation()
    }

    private fun beginDrag(id: Long) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == pendingKey(id) } ?: return
        settleJob?.cancel()
        settlingId = null
        autoScrollJob?.cancel()
        startOffset = info.offset.toFloat()
        totalDrag = 0f
        translation = 0f
        orderAtGrab = orderedIds
        draggingId = id
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        autoScrollJob = scope.launch { autoScrollLoop(id) }
    }

    private fun applyTranslation() {
        val id = draggingId ?: return
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == pendingKey(id) }
        val layoutOffset = info?.offset?.toFloat() ?: startOffset
        translation = startOffset + totalDrag - layoutOffset
    }

    private fun swapIfNeeded() {
        val id = draggingId ?: return
        val visible = listState.layoutInfo.visibleItemsInfo
        val self = visible.firstOrNull { it.key == pendingKey(id) } ?: return
        // 卡片视觉中心落在谁身上，就和谁换位
        val center = self.offset + translation + self.size / 2f
        val target = visible.firstOrNull {
            val key = it.key as? String ?: return@firstOrNull false
            key.startsWith(PENDING_KEY_PREFIX) && it.key != self.key &&
                center >= it.offset && center <= it.offset + it.size
        } ?: return
        val key = target.key as? String ?: return
        val targetId = key.removePrefix(PENDING_KEY_PREFIX).toLongOrNull() ?: return
        val from = orderedIds.indexOf(id)
        val to = orderedIds.indexOf(targetId)
        if (from < 0 || to < 0 || from == to) return
        orderedIds = orderedIds.toMutableList().apply { add(to, removeAt(from)) }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    private fun endDrag() {
        val id = draggingId ?: return
        settlingId = if (!reduceMotion && translation != 0f) id else null
        draggingId = null
        autoScrollJob?.cancel()
        autoScrollJob = null
        if (orderedIds != orderAtGrab) onCommit(orderedIds)
        if (reduceMotion || translation == 0f) {
            translation = 0f
            return
        }
        settleJob?.cancel()
        settleJob = scope.launch {
            val from = translation
            animate(from, 0f, animationSpec = XijiMotion.settle(false)) { value, _ ->
                translation = value
            }
            translation = 0f
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
                    top < edgePx -> top - edgePx
                    bottom > viewport - edgePx -> bottom - (viewport - edgePx)
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

package com.xiji.habits.ui.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.ScheduleSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 拖拽排序的纯逻辑：槽位回填与本地顺序的合并规则。
 * 手势本身在 ReorderInteractionTest 里跑真实的 Compose 触摸事件。
 */
class ReorderLogicTest {

    private val today = LocalDate.of(2026, 10, 23)

    private fun habit(id: Long, name: String = "习惯$id") = Habit(
        id = id, name = name, description = "", icon = "book", color = 0xFF416B5B,
        categoryId = null, type = HabitType.COMPLETE, targetValue = 1.0, targetUnit = "",
        schedule = ScheduleSpec.daily(), startDate = today.minusDays(30), sortOrder = id.toInt(),
        archivedAt = null, confirmQuitDaily = false, createdAt = 0, updatedAt = 0
    )

    private fun row(id: Long, done: Boolean = false) = HabitRowUi(
        habit = habit(id), state = if (done) DayState.DONE else DayState.PENDING,
        record = null, streakCount = 0, streakUnit = "天", targetValue = 1.0,
        weekDone = 0, weekTarget = 0, categoryName = null, isPaused = false
    )

    private fun state() = ReorderState(
        listState = LazyListState(),
        haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
        },
        scope = CoroutineScope(Dispatchers.Unconfined),
        reduceMotion = true,
        edgePx = 0f,
        onCommit = {}
    )

    @Test
    fun `splice keeps done habits in their slots`() {
        val all = listOf(1L, 2L, 3L, 4L)
        val isPending = listOf(true, true, false, true)
        // 待完成顺序反转：1,2,4 -> 4,2,1；已完成的 3 必须留在原来的槽位
        val result = splicePendingOrder(all, isPending, listOf(4L, 2L, 1L))
        assertEquals(listOf(4L, 2L, 3L, 1L), result)
    }

    @Test
    fun `splice ignores mismatched input`() {
        val all = listOf(1L, 2L, 3L)
        assertEquals(all, splicePendingOrder(all, listOf(true, true, true), listOf(1L, 2L)))
        assertEquals(all, splicePendingOrder(all, listOf(true, true), listOf(3L, 2L, 1L)))
    }

    @Test
    fun `sync follows repository order when not sorting`() {
        val state = state()
        state.sync(listOf(row(1), row(2), row(3)), reorderMode = false)
        assertEquals(listOf(1L, 2L, 3L), state.orderedIds)
        state.sync(listOf(row(3), row(1), row(2)), reorderMode = false)
        assertEquals(listOf(3L, 1L, 2L), state.orderedIds)
    }

    @Test
    fun `sync ignores repository order while sorting and drops removed habits`() {
        val state = state()
        state.sync(listOf(row(1), row(2), row(3)), reorderMode = false)
        state.sync(listOf(row(1), row(2), row(3)), reorderMode = true)
        // 排序模式下来自仓库的顺序变化不应把卡片拽回去（本地顺序优先）
        state.sync(listOf(row(2), row(1), row(3)), reorderMode = true)
        assertEquals(listOf(1L, 2L, 3L), state.orderedIds)
        // 归档掉 1 之后，只剩下 2 和 3
        state.sync(listOf(row(2), row(3)), reorderMode = true)
        assertEquals(listOf(2L, 3L), state.orderedIds)
        // 新增的习惯追加到末尾，不打断已有顺序
        state.sync(listOf(row(2), row(3), row(4)), reorderMode = true)
        assertEquals(listOf(2L, 3L, 4L), state.orderedIds)
    }

    @Test
    fun `ordered maps rows by local order`() {
        val state = state()
        state.sync(listOf(row(1), row(2), row(3)), reorderMode = false)
        // 传进来的 rows 顺序与本地记录不同，输出应服从本地顺序
        val shuffled = listOf(row(3), row(2), row(1))
        assertEquals(listOf(1L, 2L, 3L), state.ordered(shuffled).map { it.habit.id })
    }

    @Test
    fun `move reorders without dragging and commits`() {
        var committed: List<Long>? = null
        val state = ReorderState(
            listState = LazyListState(),
            haptics = object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
            },
            scope = CoroutineScope(Dispatchers.Unconfined),
            reduceMotion = true,
            edgePx = 0f,
            onCommit = { committed = it }
        )
        state.sync(listOf(row(1), row(2), row(3)), reorderMode = false)
        // TalkBack 的「上移 / 下移」等价于一次完整的拖动
        state.move(3, -1)
        assertEquals(listOf(1L, 3L, 2L), state.orderedIds)
        assertEquals(listOf(1L, 3L, 2L), committed)
        state.move(1, 1)
        assertEquals(listOf(3L, 1L, 2L), committed)
        // 越界时不动
        state.move(3, -5)
        assertEquals(listOf(3L, 1L, 2L), state.orderedIds)
        state.move(2, 5)
        assertEquals(listOf(3L, 1L, 2L), state.orderedIds)
    }

    @Test
    fun `leaving sorting keeps optimistic order until repository acknowledges it`() {
        val state = state()
        val original = listOf(row(1), row(2), row(3))
        state.sync(original, reorderMode = true)
        state.move(1, 1)
        state.sync(original, reorderMode = false)
        assertEquals("数据库回传旧快照时不能先跳回原顺序", listOf(2L, 1L, 3L), state.orderedIds)
        state.sync(listOf(row(2), row(1), row(3)), reorderMode = false)
        state.sync(listOf(row(3), row(2), row(1)), reorderMode = false)
        assertEquals("确认保存后仍应接受后续外部顺序", listOf(3L, 2L, 1L), state.orderedIds)
    }

    @Test
    fun `ordered falls back when rows are not in sync`() {
        val state = state()
        val rows = listOf(row(1), row(2))
        // 还没有任何同步时，直接按传入顺序显示
        assertTrue(state.ordered(rows).map { it.habit.id } == listOf(1L, 2L))
        state.sync(rows, reorderMode = false)
        // 仓库多出一行（新建）而本地还没同步，则不能丢行
        val fresh = listOf(row(1), row(2), row(3))
        assertEquals(listOf(1L, 2L, 3L), state.ordered(fresh).map { it.habit.id })
    }
}

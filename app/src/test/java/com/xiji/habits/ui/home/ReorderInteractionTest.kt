package com.xiji.habits.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSpec
import com.xiji.habits.ui.theme.AccentTheme
import com.xiji.habits.ui.theme.LocalXijiColors
import com.xiji.habits.ui.theme.ThemeMode
import com.xiji.habits.ui.theme.XijiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * 用真实的 Compose 触摸事件驱动排序把手：
 * 进入排序模式 → 按住第一张卡片向下拖一行 → 松手后提交的顺序应当交换。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReorderInteractionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 10, 23)
    private var committed: List<Long>? = null

    private fun habit(id: Long, name: String) = Habit(
        id = id, name = name, description = "", icon = "book", color = 0xFF416B5B,
        categoryId = null, type = HabitType.COMPLETE, targetValue = 1.0, targetUnit = "",
        schedule = ScheduleSpec.daily(), startDate = today.minusDays(30), sortOrder = id.toInt(),
        archivedAt = null, confirmQuitDaily = false, createdAt = 0, updatedAt = 0
    )

    private fun row(id: Long, name: String, done: Boolean = false) = HabitRowUi(
        habit = habit(id, name),
        state = if (done) DayState.DONE else DayState.PENDING,
        record = null, streakCount = 6, streakUnit = "天", targetValue = 1.0,
        weekDone = 0, weekTarget = 0, categoryName = null, isPaused = false
    )

    private fun content(
        rows: List<HabitRowUi> = listOf(
            row(1, "阅读"), row(2, "跑步"), row(3, "冥想"), row(4, "写日记", done = true)
        )
    ) {
        val state = HomeUiState(
            today = today, selectedDate = today,
            greeting = "早上好，今天也要加油。",
            dateLine = "2026年10月23日 · 星期五",
            completed = 1, total = 4, progress = 0.25f,
            encouragement = "已经完成 1 个，继续保持。",
            weekDays = (0..6).map { i ->
                DayBarItem(
                    today.minusDays((6 - i).toLong()),
                    listOf("六", "日", "一", "二", "三", "四", "五")[i],
                    today.minusDays((6 - i).toLong()).dayOfMonth,
                    i == 6, false, 0, 0, false
                )
            },
            rows = rows, filter = HabitFilter.ALL, loading = false
        )
        rule.setContent {
            XijiTheme(themeMode = ThemeMode.LIGHT, accent = AccentTheme.FOREST) {
                Box(Modifier.fillMaxSize().background(LocalXijiColors.current.pageBackground)) {
                    TodayScreen(
                        state = state,
                        onSelectDate = {}, onToggle = {}, onOpenDetail = {}, onLongPress = {},
                        onAdd = {}, onFilter = {}, onUndo = {}, onConsumeSnack = {},
                        onToggleCollapse = {},
                        snackbarHostState = SnackbarHostState(),
                        onReorderAll = { committed = it }
                    )
                }
            }
        }
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        val activity = rule.activity
        val view = activity.window.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(
            if (view.width > 0) view.width else 1080,
            if (view.height > 0) view.height else 2340,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        activity.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
        rule.waitForIdle()
        File("build/screenshots").mkdirs()
        File("build/screenshots/$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun reorderModeShowsHandles() {
        content()
        rule.onNodeWithText("排序").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("拖动 阅读 调整顺序").assertExists()
        rule.onNodeWithContentDescription("拖动 冥想 调整顺序").assertExists()
        // 已完成的行没有把手（只在待完成里排序）
        rule.onNodeWithContentDescription("拖动 写日记 调整顺序").assertDoesNotExist()
        screenshot("reorder-mode")
    }

    @Test
    fun draggingOneCardDoesNotTranslateOtherCards() {
        content()
        rule.onNodeWithText("排序").performClick()
        rule.waitForIdle()
        val dragged = rule.onNodeWithContentDescription("拖动 阅读 调整顺序")
        val neighbour = rule.onNodeWithContentDescription("拖动 跑步 调整顺序")
        val originalDraggedTop = dragged.fetchSemanticsNode().boundsInRoot.top
        val originalNeighbourTop = neighbour.fetchSemanticsNode().boundsInRoot.top

        // 小幅拖动，不跨越下一张卡片，旁边的卡片应当保持原位。
        rule.mainClock.autoAdvance = false
        dragged.performTouchInput {
            down(center)
            moveBy(Offset(0f, 12.dp.toPx()))
            moveBy(Offset(0f, 16.dp.toPx()))
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val draggedTop = dragged.fetchSemanticsNode().boundsInRoot.top
        val neighbourTop = neighbour.fetchSemanticsNode().boundsInRoot.top
        dragged.performTouchInput { up() }
        rule.mainClock.advanceTimeBy(32)
        rule.waitForIdle()
        val settlingNeighbourTop = neighbour.fetchSemanticsNode().boundsInRoot.top
        rule.mainClock.autoAdvance = true

        assertTrue("被拖动的卡片应跟随手指", draggedTop > originalDraggedTop + 1f)
        assertEquals("未拖动的卡片不应共享手势位移", originalNeighbourTop, neighbourTop, 1f)
        assertEquals("松手回弹也不应移动邻近卡片", originalNeighbourTop, settlingNeighbourTop, 1f)
    }

    @Test
    fun draggingAcrossDifferentHeightsStaysUnderTheFinger() {
        val water = row(2, "喝水").let {
            it.copy(
                habit = it.habit.copy(type = HabitType.QUANTITY, targetValue = 8.0, targetUnit = "杯"),
                state = DayState.PARTIAL,
                record = HabitRecord(2, 2, today, RecordStatus.FAILED, 5.0, null, 0, 0)
            )
        }
        content(listOf(row(1, "学习"), water, row(5, "散步"), row(3, "写日记", done = true), row(4, "冥想", done = true)))
        rule.onNodeWithText("排序").performClick()
        rule.waitForIdle()
        val dragged = rule.onNodeWithContentDescription("拖动 学习 调整顺序")
        val root = rule.onRoot()
        val start = dragged.fetchSemanticsNode().boundsInRoot.center
        rule.mainClock.autoAdvance = false
        root.performTouchInput {
            down(start)
            moveBy(Offset(0f, 24.dp.toPx()))
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val initialTop = dragged.fetchSemanticsNode().boundsInRoot.top
        val step = with(rule.density) { 20.dp.toPx() }
        val positions = mutableListOf<Float>()
        repeat(6) {
            // 从固定的根节点注入手势，每次移动后都真实布局一帧。
            root.performTouchInput { moveBy(Offset(0f, step)) }
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            positions += dragged.fetchSemanticsNode().boundsInRoot.top
        }
        root.performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        println("逐帧拖动：起点 $initialTop，每帧步长 $step，实际位置 $positions")
        positions.forEachIndexed { index, top ->
            assertEquals("第 ${index + 1} 帧换位后卡片仍应跟随手指", initialTop + step * (index + 1), top, 4f)
        }
        rule.waitForIdle()
        assertEquals(listOf(2L, 1L, 5L, 3L, 4L), committed)
    }

    @Test
    fun tallerQuantityCardCanMoveAboveAShortCard() {
        val water = row(2, "喝水").let {
            it.copy(
                habit = it.habit.copy(type = HabitType.QUANTITY, targetValue = 8.0, targetUnit = "杯"),
                state = DayState.PARTIAL,
                record = HabitRecord(2, 2, today, RecordStatus.FAILED, 5.0, null, 0, 0)
            )
        }
        content(listOf(row(1, "学习"), water, row(3, "写日记", done = true)))
        rule.onNodeWithText("排序").performClick()
        rule.waitForIdle()
        val start = rule.onNodeWithContentDescription("拖动 喝水 调整顺序").fetchSemanticsNode().boundsInRoot.center
        val root = rule.onRoot()
        rule.mainClock.autoAdvance = false
        root.performTouchInput { down(start) }
        repeat(8) {
            root.performTouchInput { moveBy(Offset(0f, -20.dp.toPx())) }
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
        root.performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals("带进度条的高卡片也能上移，不能被分组边界卡住", listOf(2L, 1L, 3L), committed)
    }

    @Test
    fun slowShortDragDoesNotSwapBackAndForth() {
        content()
        rule.onNodeWithText("排序").performClick()
        rule.waitForIdle()
        val handle = rule.onNodeWithContentDescription("拖动 阅读 调整顺序")
        val start = handle.fetchSemanticsNode().boundsInRoot.center
        val root = rule.onRoot()
        rule.mainClock.autoAdvance = false
        root.performTouchInput {
            down(start)
            moveBy(Offset(0f, 24.dp.toPx()))
        }
        repeat(25) {
            root.performTouchInput { moveBy(Offset(0f, 2.dp.toPx())) }
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
        root.performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals("卡片中心尚未经过下一张卡片中心，不应提前换位", null, committed)
    }

    @Test
    fun cancelledDragDoesNotSaveAnAccidentalOrder() {
        content()
        rule.onNodeWithText("排序").performClick()
        rule.waitForIdle()
        val handle = rule.onNodeWithContentDescription("拖动 阅读 调整顺序")
        val start = handle.fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {
            down(start)
            moveBy(Offset(0f, 24.dp.toPx()))
            moveBy(Offset(0f, 110.dp.toPx()))
            cancel()
        }
        rule.waitForIdle()
        assertEquals("系统取消手势时不应保存未确认的顺序", null, committed)
        val first = handle.fetchSemanticsNode().boundsInRoot.top
        val second = rule.onNodeWithContentDescription("拖动 跑步 调整顺序").fetchSemanticsNode().boundsInRoot.top
        assertTrue("取消手势后恢复原顺序", first < second)
    }

    @Test
    fun lastPendingCardDoesNotCoverTheCompletedGroup() {
        content(listOf(row(1, "学习"), row(2, "喝水"), row(3, "写日记", done = true), row(4, "冥想", done = true)))
        rule.onNodeWithText("排序").performClick()
        rule.waitForIdle()
        val handle = rule.onNodeWithContentDescription("拖动 喝水 调整顺序")
        val originalTop = handle.fetchSemanticsNode().boundsInRoot.top
        val start = handle.fetchSemanticsNode().boundsInRoot.center
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput {
            down(start)
            moveBy(Offset(0f, 24.dp.toPx()))
            moveBy(Offset(0f, 120.dp.toPx()))
        }
        rule.mainClock.advanceTimeBy(64)
        rule.waitForIdle()
        val heldTop = handle.fetchSemanticsNode().boundsInRoot.top
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        assertEquals("最后一张待完成卡片不能拖进已完成分组", originalTop, heldTop, 4f)
    }

    @Test
    fun releasingOnTheSwapFrameDoesNotTeleportTheCard() {
        content()
        rule.onNodeWithText("排序").performClick()
        rule.waitForIdle()
        val handle = rule.onNodeWithContentDescription("拖动 阅读 调整顺序")
        val originalTop = handle.fetchSemanticsNode().boundsInRoot.top
        val start = handle.fetchSemanticsNode().boundsInRoot.center
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput {
            down(start)
            moveBy(Offset(0f, 24.dp.toPx()))
            moveBy(Offset(0f, 100.dp.toPx()))
            up()
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val firstFrameTop = handle.fetchSemanticsNode().boundsInRoot.top
        rule.mainClock.autoAdvance = true
        val maxMove = with(rule.density) { 140.dp.toPx() }
        assertTrue("松手换位的第一帧不能额外跳过一个卡片高度", firstFrameTop - originalTop <= maxMove)
        rule.waitForIdle()
        assertEquals(listOf(2L, 1L, 3L, 4L), committed)
    }

    @Test
    fun holdingAtTheEdgeContinuesReorderingALongList() {
        content((1L..12L).map { row(it, "习惯$it") })
        rule.onNodeWithText("排序").performClick()
        rule.waitForIdle()
        val root = rule.onRoot()
        val start = rule.onNodeWithContentDescription("拖动 习惯1 调整顺序").fetchSemanticsNode().boundsInRoot.center
        val destination = root.fetchSemanticsNode().boundsInRoot.bottom - with(rule.density) { 56.dp.toPx() }
        rule.mainClock.autoAdvance = false
        root.performTouchInput {
            down(start)
            moveBy(Offset(0f, 24.dp.toPx()))
            moveTo(Offset(start.x, destination))
        }
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        root.performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertTrue("手指停在边缘时，仍应自动滚动并跨过后续卡片", (committed?.indexOf(1L) ?: -1) >= 4)
        assertEquals((1L..12L).toSet(), committed?.toSet())
    }

    @Test
    fun draggingFirstCardDownSwapsItWithTheNext() {
        content()
        rule.onNodeWithText("排序").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("拖动 阅读 调整顺序").performTouchInput {
            down(center)
            moveBy(Offset(0f, 24.dp.toPx()))
            moveBy(Offset(0f, 40.dp.toPx()))
            moveBy(Offset(0f, 40.dp.toPx()))
            moveBy(Offset(0f, 16.dp.toPx()))
            up()
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        assertEquals(listOf(2L, 1L, 3L, 4L), committed)
        screenshot("reorder-after-drag")
    }
}

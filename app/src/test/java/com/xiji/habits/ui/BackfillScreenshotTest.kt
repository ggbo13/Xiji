package com.xiji.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSpec
import com.xiji.habits.ui.calendar.CalendarDayEntry
import com.xiji.habits.ui.calendar.CalendarScreen
import com.xiji.habits.ui.calendar.CalendarUiState
import com.xiji.habits.ui.habits.DayDetailItem
import com.xiji.habits.ui.habits.DayEditSheet
import com.xiji.habits.ui.theme.AccentTheme
import com.xiji.habits.ui.theme.LocalXijiColors
import com.xiji.habits.ui.theme.ThemeMode
import com.xiji.habits.ui.theme.XijiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackfillScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val today = LocalDate.of(2026, 10, 23)

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            XijiTheme(themeMode = ThemeMode.LIGHT, accent = AccentTheme.FOREST) {
                Box(Modifier.fillMaxSize().background(LocalXijiColors.current.pageBackground)) { content() }
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        val a = rule.activity
        val v = a.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(
            if (v.width > 0) v.width else 1080,
            if (v.height > 0) v.height else 2340,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        a.runOnUiThread { v.draw(android.graphics.Canvas(bmp)) }
        rule.waitForIdle()
        File("build/screenshots").mkdirs()
        File("build/screenshots/$name.png").outputStream().use {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun habit(id: Long, name: String, icon: String, color: Long) = Habit(
        id = id, name = name, description = "", icon = icon, color = color, categoryId = null,
        type = com.xiji.habits.domain.model.HabitType.COMPLETE, targetValue = 1.0, targetUnit = "",
        schedule = ScheduleSpec.daily(), startDate = today.minusDays(90), sortOrder = id.toInt(),
        archivedAt = null, confirmQuitDaily = false, createdAt = 0, updatedAt = 0
    )

    @Test
    fun calendarBackfillMode() {
        val month = YearMonth.of(2026, 10)
        val selected = today.minusDays(2)
        val states = (1..31).associate { d ->
            val date = month.atDay(d)
            date to when {
                date.isAfter(today) -> DayState.FUTURE
                d % 7 == 0 -> DayState.FAILED
                d % 3 == 0 -> DayState.SKIPPED
                else -> DayState.DONE
            }
        }
        shot("backfill-calendar") {
            CalendarScreen(
                state = CalendarUiState(
                    month = month, today = today, selectedDate = selected,
                    monthState = states,
                    dayEntries = listOf(
                        CalendarDayEntry(
                            habit(1, "阅读 30 分钟", "book", 0xFF416B5B),
                            HabitRecord(1, 1, selected, RecordStatus.DONE, 1.0, null, 0, 0, isBackfilled = true),
                            DayState.DONE, 1.0
                        ),
                        CalendarDayEntry(habit(2, "跑步", "run", 0xFFB5713F), null, DayState.PENDING, 1.0),
                        CalendarDayEntry(habit(3, "冥想", "meditate", 0xFF8A6EA8), null, DayState.PENDING, 1.0)
                    ),
                    dayRate = 0.33f, monthDone = 18, monthScheduled = 23,
                    loading = false, backfillMode = true
                ),
                onPrev = {}, onNext = {}, onSelectDate = {}, onSetStatus = { _, _, _ -> },
                onClear = { _, _ -> }, onAddValue = { _, _, _ -> }, onOpenHabit = {},
                onToggleBackfillMode = {}, onBackfill = { _, _ -> }, onBackfillAll = {}
            )
        }
    }

    /**
     * 补签面板是独立的 dialog window，decorView 截图捕获不到，
     * 因此改用语义节点断言，确认面板内容真的组合出来了。
     */
    @Test
    fun dayEditSheetShowsBackfillCopy() {
        val date = today.minusDays(3)
        val h = habit(1, "阅读 30 分钟", "book", 0xFF416B5B)
        rule.setContent {
            XijiTheme(themeMode = ThemeMode.LIGHT, accent = AccentTheme.FOREST) {
                DayEditSheet(
                    item = DayDetailItem(h, null, DayState.PENDING, 1.0),
                    date = date,
                    today = today,
                    onDismiss = {},
                    onSetStatus = {}, onClear = {}, onAddValue = {}, onUndoValue = {}, onSetNote = {}
                )
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        rule.onNodeWithText("补签：记录 3 天前的情况").assertExists()
        rule.onNodeWithText("补签只是补记事实，不会带来额外奖励，也不会改写已完成的历史。").assertExists()
        rule.onNodeWithText("补签备注").assertExists()
        rule.onNodeWithText("完成").assertExists()
        rule.onNodeWithText("清除").assertExists()
    }

    @Test
    fun dayEditSheetTodayHasNoBackfillCopy() {
        val h = habit(1, "阅读 30 分钟", "book", 0xFF416B5B)
        rule.setContent {
            XijiTheme(themeMode = ThemeMode.LIGHT, accent = AccentTheme.FOREST) {
                DayEditSheet(
                    item = DayDetailItem(h, null, DayState.PENDING, 1.0),
                    date = today,
                    today = today,
                    onDismiss = {},
                    onSetStatus = {}, onClear = {}, onAddValue = {}, onUndoValue = {}, onSetNote = {}
                )
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        rule.onNodeWithText("当天备注").assertExists()
        rule.onAllNodesWithText("补签备注").assertCountEquals(0)
    }
}

package com.xiji.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSpec
import com.xiji.habits.domain.statistics.StatisticsEngine
import com.xiji.habits.ui.habits.DayDetailItem
import com.xiji.habits.ui.habits.DetailUiState
import com.xiji.habits.ui.habits.HabitDetailScreen
import com.xiji.habits.ui.home.DayBarItem
import com.xiji.habits.ui.home.HabitFilter
import com.xiji.habits.ui.home.HabitRowUi
import com.xiji.habits.ui.home.HomeUiState
import com.xiji.habits.ui.home.TodayScreen
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
class ScreenshotTest3 {

    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val today = LocalDate.of(2026, 10, 23)

    private fun shot(name: String, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            XijiTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, accent = AccentTheme.FOREST) {
                Box(Modifier.fillMaxSize().background(LocalXijiColors.current.pageBackground)) { content() }
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1200)
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

    private val habit = Habit(
        id = 1, name = "阅读 30 分钟", description = "睡前读一会儿书，不看手机。", icon = "book",
        color = 0xFF416B5B, categoryId = null, type = HabitType.COMPLETE, targetValue = 1.0,
        targetUnit = "", schedule = ScheduleSpec.daily(), startDate = today.minusDays(200),
        sortOrder = 0, archivedAt = null, confirmQuitDaily = false, createdAt = 0, updatedAt = 0
    )

    @Test
    fun detail() {
        val month = YearMonth.of(2026, 10)
        val monthState = (1..31).associate { d ->
            val date = month.atDay(d)
            date to when {
                date.isAfter(today) -> DayState.FUTURE
                d % 6 == 0 -> DayState.FAILED
                d % 9 == 0 -> DayState.SKIPPED
                else -> DayState.DONE
            }
        }
        val heat = (0 until 300).map { i ->
            val d = today.minusDays((299 - i).toLong())
            StatisticsEngine.DayAggregate(d, 1, if (i % 6 == 0) 0 else 1, 0, 0, 0, false)
        }
        val trend = (0..29).map { i ->
            StatisticsEngine.DayAggregate(today.minusDays((29 - i).toLong()), 1, if (i % 5 == 0) 0 else 1, 0, 0, 0, false)
        }
        shot("detail", false) {
            HabitDetailScreen(
                state = DetailUiState(
                    habit = habit,
                    stats = StatisticsEngine.HabitStats(1, 128, 128, 12, 46, "天", 0.7f, 0.66f, 0.72f, 84, 5, 7, 21, 31),
                    month = month,
                    selectedDate = today,
                    selectedItems = listOf(
                        DayDetailItem(habit, HabitRecord(1, 1, today, RecordStatus.DONE, 1.0, null, 0, 0), DayState.DONE, 1.0)
                    ),
                    heatmap = heat,
                    trend = trend,
                    history = listOf(
                        HabitRecord(1, 1, today, RecordStatus.DONE, 1.0, "读完了第三章", 0, 0),
                        HabitRecord(2, 1, today.minusDays(1), RecordStatus.DONE, 1.0, null, 0, 0),
                        HabitRecord(3, 1, today.minusDays(2), RecordStatus.FAILED, 0.0, null, 0, 0)
                    ),
                    monthState = monthState,
                    loading = false,
                    monthRate = 0.78f
                ),
                onBack = {}, onEdit = {}, onPrevMonth = {}, onNextMonth = {}, onSelectDate = {},
                onToggleToday = {}, onSetStatus = { _, _ -> }, onClear = {}, onAddValue = { _, _ -> },
                onUndoValue = {}, onSetNote = { _, _ -> }, onTogglePause = {}, onArchive = {},
                onRequestDelete = {}, onCancelDelete = {}, onConfirmDelete = {}, onConsumeSnack = {}
            )
        }
    }

    @Test
    fun emptyState() {
        shot("empty", false) {
            TodayScreen(
                state = HomeUiState(
                    today = today, selectedDate = today,
                    greeting = "早上好，今天也要加油。",
                    dateLine = "2026年10月23日 · 星期五",
                    completed = 0, total = 0, progress = 0f,
                    encouragement = "今天还没有安排，先创建一个习惯吧。",
                    weekDays = (0..6).map { i ->
                        DayBarItem(today.minusDays((2 - i).toLong()), listOf("一","二","三","四","五","六","日")[i],
                            today.minusDays((2 - i).toLong()).dayOfMonth, i == 2, i == 2, 0, 0, i > 2)
                    },
                    rows = emptyList(), filter = HabitFilter.ALL, loading = false
                ),
                onSelectDate = {}, onToggle = {}, onOpenDetail = {}, onLongPress = {}, onAdd = {},
                onFilter = {}, onUndo = {}, onConsumeSnack = {}, onToggleCollapse = {},
                snackbarHostState = androidx.compose.material3.SnackbarHostState()
            )
        }
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h640dp-xhdpi")
    fun narrowLongName() {
        val longHabit = habit.copy(name = "每天早上起床后先喝一杯温水并做十分钟拉伸")
        shot("narrow", false) {
            TodayScreen(
                state = HomeUiState(
                    today = today, selectedDate = today,
                    greeting = "晚上好，今天辛苦了。",
                    dateLine = "2026年10月23日 · 星期五",
                    completed = 1, total = 2, progress = 0.5f,
                    encouragement = "已经完成大半，继续保持。",
                    weekDays = (0..6).map { i ->
                        DayBarItem(today.minusDays((2 - i).toLong()), listOf("一","二","三","四","五","六","日")[i],
                            today.minusDays((2 - i).toLong()).dayOfMonth, i == 2, i == 2, 1, 2, i > 2)
                    },
                    rows = listOf(
                        HabitRowUi(longHabit, DayState.PENDING, null, 12, "天", 1.0, 0, 0, null, false),
                        HabitRowUi(habit.copy(id = 2, name = "跑步", icon = "run"), DayState.DONE, null, 4, "周", 1.0, 2, 3, null, false)
                    ),
                    filter = HabitFilter.ALL, loading = false
                ),
                onSelectDate = {}, onToggle = {}, onOpenDetail = {}, onLongPress = {}, onAdd = {},
                onFilter = {}, onUndo = {}, onConsumeSnack = {}, onToggleCollapse = {},
                snackbarHostState = androidx.compose.material3.SnackbarHostState()
            )
        }
    }
}

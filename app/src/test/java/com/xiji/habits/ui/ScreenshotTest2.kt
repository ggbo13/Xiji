package com.xiji.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiji.habits.data.preferences.UserSettings
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitCategory
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSpec
import com.xiji.habits.domain.model.ScheduleType
import com.xiji.habits.domain.statistics.StatisticsEngine
import com.xiji.habits.ui.calendar.CalendarDayEntry
import com.xiji.habits.ui.calendar.CalendarScreen
import com.xiji.habits.ui.calendar.CalendarUiState
import com.xiji.habits.ui.habits.EditUiState
import com.xiji.habits.ui.habits.HabitEditScreen
import com.xiji.habits.ui.settings.SettingsScreen
import com.xiji.habits.ui.settings.SettingsUiState
import com.xiji.habits.ui.statistics.HabitStatRow
import com.xiji.habits.ui.statistics.StatsScreen
import com.xiji.habits.ui.statistics.StatsUiState
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
class ScreenshotTest2 {

    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val today = LocalDate.of(2026, 10, 23)

    private fun habit(id: Long, name: String, icon: String, color: Long, type: HabitType = HabitType.COMPLETE) = Habit(
        id = id, name = name, description = "", icon = icon, color = color, categoryId = null,
        type = type, targetValue = 2000.0, targetUnit = "mL", schedule = ScheduleSpec.daily(),
        startDate = today.minusDays(90), sortOrder = id.toInt(), archivedAt = null,
        confirmQuitDaily = true, createdAt = 0, updatedAt = 0
    )

    private fun shot(name: String, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            XijiTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, accent = AccentTheme.FOREST) {
                Box(Modifier.fillMaxSize().background(LocalXijiColors.current.pageBackground)) { content() }
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1200)
        rule.waitForIdle()
        val activity = rule.activity
        val view = activity.window.decorView
        val w = if (view.width > 0) view.width else 1080
        val h = if (view.height > 0) view.height else 2340
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        activity.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        rule.waitForIdle()
        File("build/screenshots").mkdirs()
        File("build/screenshots/$name.png").outputStream().use {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun calendar() {
        val month = YearMonth.of(2026, 10)
        val states = (1..31).associate { d ->
            val date = month.atDay(d)
            val s = when {
                date.isAfter(today) -> DayState.FUTURE
                d % 7 == 0 -> DayState.FAILED
                d % 5 == 0 -> DayState.PARTIAL
                d % 3 == 0 -> DayState.SKIPPED
                else -> DayState.DONE
            }
            date to s
        }
        val h1 = habit(1, "阅读 30 分钟", "book", 0xFF416B5B)
        val h2 = habit(2, "喝水", "water", 0xFF5B7FA6, HabitType.QUANTITY)
        shot("calendar", false) {
            CalendarScreen(
                state = CalendarUiState(
                    month = month, today = today, selectedDate = today,
                    monthState = states,
                    dayEntries = listOf(
                        CalendarDayEntry(h1, HabitRecord(1, 1, today, RecordStatus.DONE, 1.0, null, 0, 0), DayState.DONE, 1.0),
                        CalendarDayEntry(h2, HabitRecord(2, 2, today, RecordStatus.FAILED, 1200.0, null, 0, 0), DayState.PARTIAL, 2000.0)
                    ),
                    dayRate = 0.5f, monthDone = 18, monthScheduled = 23, loading = false
                ),
                onPrev = {}, onNext = {}, onSelectDate = {}, onSetStatus = { _, _, _ -> },
                onClear = { _, _ -> }, onAddValue = { _, _, _ -> }, onOpenHabit = {}
            )
        }
    }

    @Test
    fun stats() {
        val h = habit(1, "阅读 30 分钟", "book", 0xFF416B5B)
        val trend = (0..29).map { i ->
            val d = today.minusDays((29 - i).toLong())
            StatisticsEngine.DayAggregate(d, 4, (i % 5), if (i % 7 == 0) 1 else 0, 0, 0, false)
        }
        shot("stats", false) {
            StatsScreen(
                state = StatsUiState(
                    overview = StatisticsEngine.Overview(328, 141, 46, "天", 0.72f, 0.64f, trend),
                    habitStats = listOf(
                        HabitStatRow(h, StatisticsEngine.HabitStats(1, 120, 120, 12, 46, "天", 0.72f, 0.64f, 0.68f, 78, 5, 7, 20, 31)),
                        HabitStatRow(habit(2, "喝水", "water", 0xFF5B7FA6, HabitType.QUANTITY), StatisticsEngine.HabitStats(2, 88, 88, 5, 21, "天", 0.5f, 0.55f, 0.6f, 61, 3, 7, 17, 31))
                    ),
                    heatmap = (0 until 300).map { i ->
                        val d = today.minusDays((299 - i).toLong())
                        StatisticsEngine.DayAggregate(d, 3, (i % 4), 0, 0, 0, false)
                    },
                    heatmapYear = 2026,
                    weekTrend = trend.takeLast(7),
                    monthTrend = trend,
                    hasData = true,
                    loading = false
                ),
                onSelectHabit = {}, onSetYear = {}, onSelectDay = {}, onOpenHabit = {}
            )
        }
    }

    @Test
    fun settings() {
        shot("settings", true) {
            SettingsScreen(
                settings = UserSettings(themeMode = ThemeMode.DARK),
                state = SettingsUiState(
                    categories = listOf(HabitCategory(1, "健康", 0, 0)),
                    habitCount = 5, recordCount = 128
                ),
                onThemeMode = {}, onAccent = {}, onDynamic = {}, onWeekStart = {},
                onReduceAnimation = {}, onOpenBackup = {}, onOpenCategories = {}, onOpenWidgetHelp = {},
                onOpenNotificationSettings = {}, onOpenExactAlarmSettings = {},
                onSaveCategory = { _, _ -> }, onDeleteCategory = {}, onEditCategory = {}, onConsumeMessage = {}
            )
        }
    }

    @Test
    fun editHabit() {
        shot("edit", false) {
            HabitEditScreen(
                state = EditUiState(
                    name = "阅读 30 分钟", description = "睡前读一会儿书，不看手机。",
                    icon = "book", color = 0xFF416B5B, type = HabitType.QUANTITY,
                    targetValue = "30", targetUnit = "分钟",
                    scheduleType = ScheduleType.WEEKDAYS, weekdaysMask = 0b0011111,
                    categories = listOf(HabitCategory(1, "学习", 0, 0)),
                    loaded = true
                ),
                onBack = {}, onName = {}, onDescription = {}, onIcon = {}, onColor = {}, onCategory = {},
                onType = {}, onTargetValue = {}, onTargetUnit = {}, onScheduleType = {},
                onToggleWeekday = {}, onTimesPerWeek = {}, onIntervalDays = {}, onStartDate = {},
                onToggleAdvanced = {}, onConfirmQuitDaily = {}, onAddReminder = {},
                onUpdateReminder = { _, _, _ -> }, onToggleReminder = {}, onRemoveReminder = {},
                onSave = {}, onSaved = {}
            )
        }
    }
}

package com.xiji.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitCategory
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSpec
import com.xiji.habits.ui.home.DayBarItem
import com.xiji.habits.ui.home.HabitFilter
import com.xiji.habits.ui.home.HabitRowUi
import com.xiji.habits.ui.home.HomeUiState
import com.xiji.habits.ui.home.TodayScreen
import com.xiji.habits.ui.theme.AccentTheme
import com.xiji.habits.ui.theme.ThemeMode
import com.xiji.habits.ui.theme.XijiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * 真实渲染测试：用 Robolectric 原生图形模式渲染 Compose 界面并导出 PNG。
 * 目的不是像素比对，而是确认页面能真实布局、不崩溃、层级合理。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @get:Rule val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val today = LocalDate.of(2026, 10, 23)

    private fun habit(
        id: Long, name: String, icon: String, color: Long,
        type: HabitType = HabitType.COMPLETE,
        schedule: ScheduleSpec = ScheduleSpec.daily(),
        target: Double = 1.0, unit: String = ""
    ) = Habit(
        id = id, name = name, description = "", icon = icon, color = color, categoryId = null,
        type = type, targetValue = target, targetUnit = unit, schedule = schedule,
        startDate = today.minusDays(60), sortOrder = id.toInt(), archivedAt = null,
        confirmQuitDaily = true, createdAt = 0, updatedAt = 0
    )

    private fun row(
        habit: Habit, state: DayState, streak: Int, value: Double = 0.0,
        unit2: String = "天", weekDone: Int = 0, weekTarget: Int = 0
    ) = HabitRowUi(
        habit = habit, state = state,
        record = if (value > 0) HabitRecord(0, habit.id, today, RecordStatus.DONE, value, null, 0, 0) else null,
        streakCount = streak, streakUnit = unit2, targetValue = habit.targetValue,
        weekDone = weekDone, weekTarget = weekTarget, categoryName = null, isPaused = false
    )

    private fun state(dark: Boolean) = HomeUiState(
        today = today,
        selectedDate = today,
        greeting = "早上好，今天也要加油。",
        dateLine = "2026年10月23日 · 星期五",
        completed = 2,
        total = 5,
        progress = 0.4f,
        encouragement = "已经完成大半，继续保持。",
        weekDays = (0..6).map { i ->
            val d = today.minusDays((2 - i).toLong())
            DayBarItem(
                date = d,
                dayLabel = listOf("一", "二", "三", "四", "五", "六", "日")[i],
                dayOfMonth = d.dayOfMonth,
                isToday = d == today,
                isSelected = d == today,
                done = if (d.isAfter(today)) 0 else i,
                scheduled = 5,
                isFuture = d.isAfter(today)
            )
        },
        rows = listOf(
            row(habit(1, "阅读 30 分钟", "book", 0xFF416B5B), DayState.PENDING, 12),
            row(habit(2, "喝水", "water", 0xFF5B7FA6, HabitType.QUANTITY, target = 2000.0, unit = "mL"), DayState.PENDING, 5, value = 1200.0),
            row(habit(3, "跑步", "run", 0xFFB5713F, schedule = ScheduleSpec.timesPerWeek(3)), DayState.DONE, 4, weekDone = 2, weekTarget = 3),
            row(habit(4, "冥想", "meditate", 0xFF8A6EA8), DayState.DONE, 21),
            row(habit(5, "不喝含糖饮料", "no_sugar", 0xFF4F7D74, HabitType.QUIT), DayState.PENDING, 9)
        ),
        categories = listOf(HabitCategory(1, "健康", 0xFF416B5B, 0), HabitCategory(2, "学习", 0xFF5B7FA6, 1)),
        filter = HabitFilter.ALL,
        loading = false
    )

    private fun render(name: String, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            XijiTheme(
                themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                accent = AccentTheme.FOREST
            ) {
                Box(Modifier.fillMaxSize().background(com.xiji.habits.ui.theme.LocalXijiColors.current.pageBackground)) {
                    content()
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1200)
        composeRule.waitForIdle()
        // 直接绘制 Activity 的 decorView，避免依赖 WindowCapture 的帧回调
        val activity = composeRule.activity
        val view = activity.window.decorView
        val width = if (view.width > 0) view.width else 1080
        val height = if (view.height > 0) view.height else 2340
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        activity.runOnUiThread { view.draw(canvas) }
        composeRule.waitForIdle()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun todayLight() {
        render("today-light", dark = false) {
            TodayScreen(
                state = state(false),
                onSelectDate = {}, onToggle = {}, onOpenDetail = {}, onLongPress = {},
                onAdd = {}, onFilter = {}, onUndo = {}, onConsumeSnack = {},
                onToggleCollapse = {}, snackbarHostState = SnackbarHostState()
            )
        }
    }

    @Test
    fun todayDark() {
        render("today-dark", dark = true) {
            TodayScreen(
                state = state(true),
                onSelectDate = {}, onToggle = {}, onOpenDetail = {}, onLongPress = {},
                onAdd = {}, onFilter = {}, onUndo = {}, onConsumeSnack = {},
                onToggleCollapse = {}, snackbarHostState = SnackbarHostState()
            )
        }
    }
}

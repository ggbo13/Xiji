package com.xiji.habits.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSpec
import com.xiji.habits.ui.theme.AccentTheme
import com.xiji.habits.ui.theme.LocalXijiColors
import com.xiji.habits.ui.theme.ThemeMode
import com.xiji.habits.ui.theme.XijiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 验证计次习惯的轻点加一与长按补满不会互相串线。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuantityQuickAddInteractionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 11, 21)
    private val additions = mutableListOf<Double>()
    private var cardLongPresses = 0
    private var detailOpens = 0

    private fun content() {
        val habit = Habit(
            id = 1,
            name = "喝水",
            description = "",
            icon = "water",
            color = 0xFF5B7FA6,
            categoryId = null,
            type = HabitType.QUANTITY,
            targetValue = 8.0,
            targetUnit = "杯",
            schedule = ScheduleSpec.daily(),
            startDate = today.minusDays(30),
            sortOrder = 1,
            archivedAt = null,
            confirmQuitDaily = false,
            createdAt = 0,
            updatedAt = 0
        )
        val row = HabitRowUi(
            habit = habit,
            state = DayState.PENDING,
            record = HabitRecord(
                id = 1,
                habitId = habit.id,
                date = today,
                status = RecordStatus.FAILED,
                accumulatedValue = 5.0,
                note = null,
                createdAt = 0,
                updatedAt = 0
            ),
            streakCount = 2,
            streakUnit = "天",
            targetValue = 8.0,
            weekDone = 0,
            weekTarget = 0,
            categoryName = null,
            isPaused = false
        )

        rule.setContent {
            XijiTheme(themeMode = ThemeMode.LIGHT, accent = AccentTheme.FOREST) {
                Box(Modifier.fillMaxSize().background(LocalXijiColors.current.pageBackground)) {
                    HabitCard(
                        row = row,
                        isToday = true,
                        onToggle = {},
                        onOpenDetail = { detailOpens++ },
                        onLongPress = { cardLongPresses++ },
                        onQuickAdd = { _, delta -> additions += delta }
                    )
                }
            }
        }
    }

    @Test
    fun tapAddsOne() {
        content()

        rule.onNodeWithContentDescription("点按加 1 喝水，长按补满").performClick()

        assertEquals(listOf(1.0), additions)
        assertEquals(0, cardLongPresses)
        assertEquals(0, detailOpens)
    }

    @Test
    fun longPressFillsRemainingTarget() {
        content()

        rule.onNodeWithContentDescription("点按加 1 喝水，长按补满")
            .performTouchInput { longClick() }
        rule.waitForIdle()

        assertEquals(listOf(3.0), additions)
        assertEquals(0, cardLongPresses)
        assertEquals(0, detailOpens)
    }
}

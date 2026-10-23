package com.xiji.habits.widget

import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.HabitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class WidgetSizeTest {

    @Test
    fun `row count grows with height`() {
        // 2×2 典型高度 160dp：header 54 + padding 24 → 82 可用 → 4 行(20dp)
        assertEquals(4, WidgetSize.rowCount(160.dp, 54.dp, 20.dp))
        // 4×2 典型高度 150dp：header 26 + padding 24 → 100 可用 → 2 行(38dp)
        assertEquals(2, WidgetSize.rowCount(150.dp, 26.dp, 38.dp))
        // 4×4 高度 300dp → 6 行
        assertEquals(6, WidgetSize.rowCount(300.dp, 26.dp, 38.dp))
    }

    @Test
    fun `row count never negative`() {
        assertEquals(0, WidgetSize.rowCount(40.dp, 26.dp, 38.dp))
        assertEquals(0, WidgetSize.rowCount(0.dp, 26.dp, 38.dp))
    }

    @Test
    fun `habit action labels match type`() {
        val complete = widgetHabit(HabitType.COMPLETE, DayState.PENDING)
        assertEquals("完成", complete.actionLabel)
        assertNull(widgetHabit(HabitType.COMPLETE, DayState.DONE).actionLabel)

        val quantity = widgetHabit(HabitType.QUANTITY, DayState.PARTIAL)
        assertEquals("+1", quantity.actionLabel)
        assertEquals("1/8杯", quantity.quantityLabel)

        val quit = widgetHabit(HabitType.QUIT, DayState.PENDING)
        assertEquals("守住", quit.actionLabel)
        assertEquals("重来", widgetHabit(HabitType.QUIT, DayState.FAILED).actionLabel)
        assertNull(widgetHabit(HabitType.QUIT, DayState.DONE).actionLabel)
    }

    private fun widgetHabit(type: HabitType, state: DayState) = WidgetHabit(
        id = 1, name = "喝水", icon = "water", color = 0, type = type, state = state,
        value = 1.0, target = 8.0, unit = "杯"
    )
}

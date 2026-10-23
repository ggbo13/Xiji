package com.xiji.habits.domain

import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitPausePeriod
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSpec
import com.xiji.habits.domain.model.ScheduleType
import com.xiji.habits.domain.model.WeekStart
import com.xiji.habits.domain.schedule.ScheduleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleEngineTest {

    private fun habit(
        type: HabitType = HabitType.COMPLETE,
        schedule: ScheduleSpec = ScheduleSpec.daily(),
        start: LocalDate = LocalDate.of(2026, 1, 1),
        target: Double = 1.0
    ) = Habit(
        id = 1,
        name = "test",
        description = "",
        icon = "star",
        color = 0xFF416B5B,
        categoryId = null,
        type = type,
        targetValue = target,
        targetUnit = "",
        schedule = schedule,
        startDate = start,
        sortOrder = 0,
        archivedAt = null,
        confirmQuitDaily = false,
        createdAt = 0,
        updatedAt = 0
    )

    private fun record(date: LocalDate, status: RecordStatus = RecordStatus.DONE, value: Double = 1.0) =
        HabitRecord(0, 1, date, status, value, null, 0, 0)

    @Test
    fun `daily streak counts consecutive days`() {
        val today = LocalDate.of(2026, 3, 10)
        val h = habit()
        val records = (0..4).map { record(today.minusDays(it.toLong())) }
        val r = ScheduleEngine.streak(h, emptyList(), emptyList(), records, today)
        assertEquals(5, r.current)
        assertEquals(5, r.longest)
    }

    @Test
    fun `unfinished today does not break streak`() {
        val today = LocalDate.of(2026, 3, 10)
        val h = habit()
        val records = (1..3).map { record(today.minusDays(it.toLong())) }
        val r = ScheduleEngine.streak(h, emptyList(), emptyList(), records, today)
        assertEquals(3, r.current)
    }

    @Test
    fun `gap breaks streak`() {
        val today = LocalDate.of(2026, 3, 10)
        val h = habit()
        val records = listOf(
            record(today), record(today.minusDays(1)),
            record(today.minusDays(3)), record(today.minusDays(4)), record(today.minusDays(5))
        )
        val r = ScheduleEngine.streak(h, emptyList(), emptyList(), records, today)
        assertEquals(2, r.current)
        assertEquals(3, r.longest)
    }

    @Test
    fun `mon wed fri habit does not break on tuesday`() {
        // 2026-03-09 是周一
        val monday = LocalDate.of(2026, 3, 9)
        val h = habit(schedule = ScheduleSpec.weekdays(0b0010101)) // 一、三、五
        val records = listOf(
            record(monday), record(monday.plusDays(2)), record(monday.plusDays(4)),
            record(monday.plusDays(7)), record(monday.plusDays(9)), record(monday.plusDays(11))
        )
        val today = monday.plusDays(11)
        val r = ScheduleEngine.streak(h, emptyList(), emptyList(), records, today)
        assertEquals(6, r.current)
    }

    @Test
    fun `times per week uses weekly quota and current week is not failed early`() {
        // 2026-03-09 周一
        val monday = LocalDate.of(2026, 3, 9)
        val h = habit(schedule = ScheduleSpec.timesPerWeek(3))
        val prevWeek = monday.minusWeeks(1)
        val records = listOf(
            record(prevWeek), record(prevWeek.plusDays(2)), record(prevWeek.plusDays(5)),
            record(monday), record(monday.plusDays(1))
        )
        val today = monday.plusDays(1)
        val r = ScheduleEngine.streak(h, emptyList(), emptyList(), records, today, WeekStart.MONDAY)
        // 上周达标 + 本周进行中(未达标不计)
        assertEquals(1, r.current)
        assertEquals("周", r.unit)
    }

    @Test
    fun `times per week completed current week counts`() {
        val monday = LocalDate.of(2026, 3, 9)
        val h = habit(schedule = ScheduleSpec.timesPerWeek(2))
        val records = listOf(record(monday), record(monday.plusDays(1)))
        val r = ScheduleEngine.streak(h, emptyList(), emptyList(), records, monday.plusDays(1), WeekStart.MONDAY)
        assertEquals(1, r.current)
    }

    @Test
    fun `interval habit only counts scheduled days`() {
        val start = LocalDate.of(2026, 3, 1)
        val h = habit(schedule = ScheduleSpec.interval(2, start), start = start)
        val records = listOf(record(start), record(start.plusDays(2)), record(start.plusDays(4)))
        val r = ScheduleEngine.streak(h, emptyList(), emptyList(), records, start.plusDays(4))
        assertEquals(3, r.current)
    }

    @Test
    fun `paused days are excluded from denominator`() {
        val today = LocalDate.of(2026, 3, 10)
        val h = habit()
        val pause = HabitPausePeriod(1, 1, today.minusDays(3), today.minusDays(1), null)
        assertFalse(ScheduleEngine.isCountableDay(h, emptyList(), listOf(pause), today.minusDays(2), today))
        assertTrue(ScheduleEngine.isCountableDay(h, emptyList(), listOf(pause), today, today))
    }

    @Test
    fun `future date is never done`() {
        val today = LocalDate.of(2026, 3, 10)
        val h = habit()
        val state = ScheduleEngine.dayState(h, emptyList(), emptyList(), record(today.plusDays(1)), today.plusDays(1), today)
        assertEquals(DayState.FUTURE, state)
    }

    @Test
    fun `quit habit unrecorded is pending not done`() {
        val today = LocalDate.of(2026, 3, 10)
        val h = habit(type = HabitType.QUIT)
        val state = ScheduleEngine.dayState(h, emptyList(), emptyList(), null, today, today)
        assertEquals(DayState.PENDING, state)
    }

    @Test
    fun `not scheduled day returns not scheduled`() {
        val today = LocalDate.of(2026, 3, 10) // 周二
        val h = habit(schedule = ScheduleSpec.weekdays(0b0010101))
        assertEquals(DayState.NOT_SCHEDULED, ScheduleEngine.dayState(h, emptyList(), emptyList(), null, today, today))
    }

    @Test
    fun `leap year feb 29 handled`() {
        val feb29 = LocalDate.of(2028, 2, 29)
        val h = habit(start = feb29.minusDays(2))
        val records = listOf(record(feb29), record(feb29.minusDays(1)), record(feb29.minusDays(2)))
        val r = ScheduleEngine.streak(h, emptyList(), emptyList(), records, feb29)
        assertEquals(3, r.current)
    }

    @Test
    fun `cross year streak`() {
        val today = LocalDate.of(2026, 1, 2)
        val h = habit(start = LocalDate.of(2025, 12, 30))
        val records = listOf(
            record(LocalDate.of(2025, 12, 30)),
            record(LocalDate.of(2025, 12, 31)),
            record(LocalDate.of(2026, 1, 1)),
            record(today)
        )
        val r = ScheduleEngine.streak(h, emptyList(), emptyList(), records, today)
        assertEquals(4, r.current)
    }

    @Test
    fun `skipped day does not break streak`() {
        val today = LocalDate.of(2026, 3, 10)
        val h = habit()
        val records = listOf(
            record(today), record(today.minusDays(1), RecordStatus.SKIPPED), record(today.minusDays(2))
        )
        val r = ScheduleEngine.streak(h, emptyList(), emptyList(), records, today)
        assertEquals(3, r.current)
    }
}

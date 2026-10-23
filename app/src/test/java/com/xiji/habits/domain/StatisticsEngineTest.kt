package com.xiji.habits.domain

import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitPausePeriod
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSpec
import com.xiji.habits.domain.model.ScheduleType
import com.xiji.habits.domain.model.WeekStart
import com.xiji.habits.domain.statistics.StatisticsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatisticsEngineTest {

    private fun habit(
        type: HabitType = HabitType.COMPLETE,
        schedule: ScheduleSpec = ScheduleSpec.daily(),
        start: LocalDate = LocalDate.of(2026, 3, 1),
        target: Double = 1.0
    ) = Habit(
        id = 1, name = "h", description = "", icon = "star", color = 0, categoryId = null,
        type = type, targetValue = target, targetUnit = "次", schedule = schedule,
        startDate = start, sortOrder = 0, archivedAt = null, confirmQuitDaily = false,
        createdAt = 0, updatedAt = 0
    )

    private fun record(date: LocalDate, status: RecordStatus = RecordStatus.DONE, value: Double = 1.0) =
        HabitRecord(0, 1, date, status, value, null, 0, 0)

    @Test
    fun `completion rate excludes non scheduled days`() {
        val start = LocalDate.of(2026, 3, 2) // 周一
        val today = start.plusDays(6) // 周日
        val h = habit(schedule = ScheduleSpec.weekdays(0b0010101), start = start)
        val records = listOf(record(start), record(start.plusDays(2))) // 周一、周三完成，周五缺
        val stats = StatisticsEngine.habitStats(h, emptyList(), emptyList(), records, today)
        // 三个计划日，两个完成
        assertEquals(2, stats.totalCompletions)
        assertTrue(stats.overallRate > 0.65f && stats.overallRate < 0.68f)
    }

    @Test
    fun `skipped is excluded from denominator`() {
        val start = LocalDate.of(2026, 3, 2)
        val today = start.plusDays(1)
        val h = habit(start = start)
        val records = listOf(record(start), record(today, RecordStatus.SKIPPED))
        val stats = StatisticsEngine.habitStats(h, emptyList(), emptyList(), records, today)
        assertEquals(1.0f, stats.overallRate, 0.001f)
    }

    @Test
    fun `quantity habit counts once per day even with many events`() {
        val start = LocalDate.of(2026, 3, 2)
        val h = habit(type = HabitType.QUANTITY, start = start, target = 2000.0)
        val records = listOf(record(start, RecordStatus.DONE, 2000.0))
        val stats = StatisticsEngine.habitStats(h, emptyList(), emptyList(), records, start.plusDays(1))
        assertEquals(1, stats.totalCompletions)
    }

    @Test
    fun `quit habit unrecorded counts as miss`() {
        val start = LocalDate.of(2026, 3, 2)
        val today = start.plusDays(3)
        val h = habit(type = HabitType.QUIT, start = start)
        val records = listOf(record(start))
        val stats = StatisticsEngine.habitStats(h, emptyList(), emptyList(), records, today)
        assertEquals(1, stats.totalCompletions)
        assertTrue(stats.overallRate < 0.34f)
    }

    @Test
    fun `strength decays but does not zero on one miss`() {
        val start = LocalDate.of(2026, 1, 1)
        val today = LocalDate.of(2026, 3, 1)
        val h = habit(start = start)
        val all = (0 until 90).map { record(today.minusDays(it.toLong())) }
        val full = StatisticsEngine.strength(h, emptyList(), emptyList(), all, today)
        assertEquals(100, full)
        val withMiss = all.drop(1) + listOf(record(today.minusDays(1), RecordStatus.FAILED))
        val reduced = StatisticsEngine.strength(h, emptyList(), emptyList(), withMiss, today)
        assertTrue(reduced in 90..99)
    }

    @Test
    fun `strength is zero with no records`() {
        val h = habit(start = LocalDate.of(2026, 3, 1))
        assertEquals(0, StatisticsEngine.strength(h, emptyList(), emptyList(), emptyList(), LocalDate.of(2026, 3, 10)))
    }

    @Test
    fun `weekly rate counts quota`() {
        val monday = LocalDate.of(2026, 3, 9)
        val h = habit(schedule = ScheduleSpec.timesPerWeek(3), start = monday)
        val records = listOf(record(monday), record(monday.plusDays(1)))
        val stats = StatisticsEngine.habitStats(h, emptyList(), emptyList(), records, monday.plusDays(2), WeekStart.MONDAY)
        assertEquals(2, stats.weekDone)
        assertEquals(3, stats.weekTarget)
    }

    @Test
    fun `paused period excluded from stats`() {
        val start = LocalDate.of(2026, 3, 2)
        val today = start.plusDays(6)
        val h = habit(start = start)
        val pause = HabitPausePeriod(1, 1, start.plusDays(3), start.plusDays(5), null)
        val records = (0..2).map { record(start.plusDays(it.toLong())) }
        val stats = StatisticsEngine.habitStats(h, emptyList(), listOf(pause), records, today)
        assertEquals(1.0f, stats.overallRate, 0.001f)
    }

    @Test
    fun `heatmap distinguishes scheduled vs unscheduled`() {
        val start = LocalDate.of(2026, 3, 2)
        val h = habit(schedule = ScheduleSpec.weekdays(0b0010101), start = start)
        val map = StatisticsEngine.heatmap(
            listOf(h), emptyMap(), emptyList(), listOf(record(start)),
            start, start.plusDays(6), start.plusDays(6)
        )
        assertEquals(7, map.size)
        assertEquals(1, map[0].done)
        assertEquals(0, map[1].scheduled) // 周二非计划
    }
}

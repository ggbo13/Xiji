package com.xiji.habits.domain

import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitPausePeriod
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSpec
import com.xiji.habits.domain.statistics.StatisticsEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 列表页用的轻量统计必须与完整统计在「连续 / 本周配额」上完全一致，
 * 否则优化会改变界面显示。
 */
class ListStatsTest {

    private fun habit(schedule: ScheduleSpec = ScheduleSpec.daily(), start: LocalDate = LocalDate.of(2026, 1, 1)) =
        Habit(
            id = 1, name = "h", description = "", icon = "s", color = 0, categoryId = null,
            type = HabitType.COMPLETE, targetValue = 1.0, targetUnit = "", schedule = schedule,
            startDate = start, sortOrder = 0, archivedAt = null, confirmQuitDaily = false,
            createdAt = 0, updatedAt = 0
        )

    private fun rec(d: LocalDate, s: RecordStatus = RecordStatus.DONE) =
        HabitRecord(0, 1, d, s, 1.0, null, 0, 0)

    @Test
    fun `listStats matches habitStats for daily habit`() {
        val today = LocalDate.of(2026, 3, 10)
        val h = habit()
        val records = (0..5).map { rec(today.minusDays(it.toLong())) }
        val full = StatisticsEngine.habitStats(h, emptyList(), emptyList(), records, today)
        val light = StatisticsEngine.listStats(h, emptyList(), emptyList(), records, today)
        assertEquals(full.currentStreak, light.currentStreak)
        assertEquals(full.longestStreak, light.longestStreak)
        assertEquals(full.weekDone, light.weekDone)
        assertEquals(full.weekTarget, light.weekTarget)
    }

    @Test
    fun `listStats matches habitStats for weekly quota`() {
        val monday = LocalDate.of(2026, 3, 9)
        val h = habit(ScheduleSpec.timesPerWeek(3), start = monday.minusWeeks(2))
        val records = listOf(rec(monday), rec(monday.plusDays(1)))
        val today = monday.plusDays(2)
        val full = StatisticsEngine.habitStats(h, emptyList(), emptyList(), records, today)
        val light = StatisticsEngine.listStats(h, emptyList(), emptyList(), records, today)
        assertEquals(full.currentStreak, light.currentStreak)
        assertEquals(full.weekDone, light.weekDone)
        assertEquals(3, light.weekTarget)
    }

    @Test
    fun `listStats matches with pauses`() {
        val today = LocalDate.of(2026, 3, 10)
        val h = habit(start = today.minusDays(10))
        val pause = HabitPausePeriod(1, 1, today.minusDays(3), today.minusDays(1), null)
        val records = (4..8).map { rec(today.minusDays(it.toLong())) }
        val full = StatisticsEngine.habitStats(h, emptyList(), listOf(pause), records, today)
        val light = StatisticsEngine.listStats(h, emptyList(), listOf(pause), records, today)
        assertEquals(full.currentStreak, light.currentStreak)
        assertEquals(full.weekDone, light.weekDone)
    }

    @Test
    fun `listStats handles empty records`() {
        val h = habit(start = LocalDate.of(2026, 3, 1))
        val light = StatisticsEngine.listStats(h, emptyList(), emptyList(), emptyList(), LocalDate.of(2026, 3, 10))
        assertEquals(0, light.currentStreak)
        assertEquals(0, light.weekDone)
    }
}

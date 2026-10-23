package com.xiji.habits.domain

import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitPausePeriod
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSpec
import com.xiji.habits.domain.model.ScheduleSnapshot
import com.xiji.habits.domain.model.ScheduleType
import com.xiji.habits.domain.schedule.ScheduleEngine
import com.xiji.habits.domain.statistics.StatisticsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ExtraLogicTest {

    private fun habit(
        type: HabitType = HabitType.COMPLETE,
        schedule: ScheduleSpec = ScheduleSpec.daily(),
        start: LocalDate = LocalDate.of(2026, 1, 1),
        target: Double = 1.0
    ) = Habit(
        id = 1, name = "h", description = "", icon = "star", color = 0, categoryId = null,
        type = type, targetValue = target, targetUnit = "ml", schedule = schedule,
        startDate = start, sortOrder = 0, archivedAt = null, confirmQuitDaily = true,
        createdAt = 0, updatedAt = 0
    )

    private fun record(date: LocalDate, status: RecordStatus = RecordStatus.DONE, value: Double = 1.0) =
        HabitRecord(0, 1, date, status, value, null, 0, 0)

    @Test
    fun `schedule snapshot keeps history when goal changes`() {
        val start = LocalDate.of(2026, 1, 1)
        val change = LocalDate.of(2026, 2, 1)
        val h = habit(schedule = ScheduleSpec.timesPerWeek(2), start = start)
        val snaps = listOf(
            ScheduleSnapshot(1, 1, start, ScheduleSpec.timesPerWeek(2), 1.0, "", 0),
            ScheduleSnapshot(2, 1, change, ScheduleSpec.timesPerWeek(5), 1.0, "", 0)
        )
        // 历史日期使用旧规则
        val old = ScheduleEngine.snapshotFor(snaps, h, start.plusDays(3))
        assertEquals(2, old?.schedule?.timesPerWeek)
        val new = ScheduleEngine.snapshotFor(snaps, h, change.plusDays(1))
        assertEquals(5, new?.schedule?.timesPerWeek)
    }

    @Test
    fun `quantity partial then reached`() {
        val today = LocalDate.of(2026, 3, 10)
        val h = habit(type = HabitType.QUANTITY, target = 2000.0, start = today)
        val partial = record(today, RecordStatus.FAILED, 800.0)
        assertEquals(
            DayState.FAILED,
            ScheduleEngine.dayState(h, emptyList(), emptyList(), partial, today, today)
        )
        assertFalse(ScheduleEngine.isQuantityReached(h, null, partial))
        val reached = record(today, RecordStatus.DONE, 2000.0)
        assertTrue(ScheduleEngine.isQuantityReached(h, null, reached))
    }

    @Test
    fun `repeated same day completion counted once`() {
        val today = LocalDate.of(2026, 3, 10)
        val h = habit(start = today.minusDays(5))
        val records = listOf(record(today), record(today))
        val stats = StatisticsEngine.habitStats(h, emptyList(), emptyList(), records, today.plusDays(1))
        // 同一天即使重复记录也只算一天
        assertEquals(1, stats.totalCompletions)
    }

    @Test
    fun `undo reduces stats`() {
        val today = LocalDate.of(2026, 3, 10)
        val h = habit(start = today.minusDays(3))
        val before = StatisticsEngine.habitStats(
            h, emptyList(), emptyList(),
            listOf(record(today), record(today.minusDays(1))), today.plusDays(1)
        )
        val after = StatisticsEngine.habitStats(
            h, emptyList(), emptyList(),
            listOf(record(today)), today.plusDays(1)
        )
        assertEquals(2, before.totalCompletions)
        assertEquals(1, after.totalCompletions)
        assertTrue(after.overallRate < before.overallRate)
    }

    @Test
    fun `weekly streak breaks on missed full week`() {
        val monday = LocalDate.of(2026, 3, 2)
        val h = habit(schedule = ScheduleSpec.timesPerWeek(2), start = monday)
        // 第一周达标，第二周（更近）未达标
        val records = listOf(
            record(monday), record(monday.plusDays(1)),
            record(monday.plusWeeks(1)), record(monday.plusWeeks(1).plusDays(1)),
            record(monday.plusWeeks(2)) // 第三周只完成一次
        )
        val today = monday.plusWeeks(3) // 第四周周一，第三周已完整结束
        val r = ScheduleEngine.streak(h, emptyList(), emptyList(), records, today)
        assertEquals(0, r.current)
        assertTrue(r.longest >= 1)
    }

    @Test
    fun `timezone independent date math`() {
        val a = LocalDate.of(2026, 3, 8)
        val b = a.plusDays(1)
        assertEquals(1L, b.toEpochDay() - a.toEpochDay())
        // 夏令时切换日（美国 3 月 8 日）在 LocalDate 语义下仍是 1 天
        assertEquals(1, java.time.temporal.ChronoUnit.DAYS.between(a, b))
    }

    @Test
    fun `system date moved backwards does not create future completions`() {
        val fakeToday = LocalDate.of(2026, 3, 10)
        val h = habit(start = LocalDate.of(2026, 3, 1))
        val state = ScheduleEngine.dayState(
            h, emptyList(), emptyList(), record(LocalDate.of(2026, 3, 12)),
            LocalDate.of(2026, 3, 12), fakeToday
        )
        assertEquals(DayState.FUTURE, state)
    }

    @Test
    fun `strength drops slightly after a few misses`() {
        val start = LocalDate.of(2025, 1, 1)
        val today = LocalDate.of(2026, 3, 1)
        val h = habit(start = start)
        val full = (0 until 90).map { record(today.minusDays(it.toLong())) }
        val fullScore = StatisticsEngine.strength(h, emptyList(), emptyList(), full, today)
        assertEquals(100, fullScore)
        // 只中断最近 3 天，其余 87 天都完成：强度小幅下降但远未清零
        val gap = (3 until 90).map { record(today.minusDays(it.toLong())) }
        val gapScore = StatisticsEngine.strength(h, emptyList(), emptyList(), gap, today)
        // 最近 3 天权重最高，因此下降约 10 分；但不会清零，也不会回到 0
        assertTrue("expected 85..95, was $gapScore", gapScore in 85..95)
        assertTrue(gapScore < fullScore)
    }

    @Test
    fun `strength reaches zero after long inactivity`() {
        val start = LocalDate.of(2025, 1, 1)
        val today = LocalDate.of(2026, 3, 1)
        val h = habit(start = start)
        // 窗口外（>90 天）没有记录 → 强度为 0，这是「当前习惯强度」而非历史功勋
        val old = (0 until 100).map { record(today.minusDays(400 - it.toLong())) }
        assertEquals(0, StatisticsEngine.strength(h, emptyList(), emptyList(), old, today))
    }

    @Test
    fun `paused habit not counted in day rate`() {
        val today = LocalDate.of(2026, 3, 10)
        val h = habit(start = today.minusDays(10))
        val pause = HabitPausePeriod(1, 1, today.minusDays(2), null, null)
        val entries = StatisticsEngine.dayEntries(
            listOf(h), emptyMap(), listOf(pause), emptyList(), today, today
        )
        assertEquals(DayState.PAUSED, entries.first().state)
        assertEquals(0f, StatisticsEngine.dayRate(entries), 0.001f)
    }

    @Test
    fun `describe text for schedules`() {
        assertEquals("每天", ScheduleSpec.daily().describe())
        assertEquals("每周一、三、五", ScheduleSpec.weekdays(0b0010101).describe())
        assertEquals("每周 3 次", ScheduleSpec.timesPerWeek(3).describe())
        assertEquals("每隔 2 天", ScheduleSpec.interval(2, LocalDate.now()).describe())
        assertEquals("工作日", ScheduleSpec.weekdays(0b0011111).describe())
        assertEquals("周末", ScheduleSpec.weekdays(0b1100000).describe())
    }
}

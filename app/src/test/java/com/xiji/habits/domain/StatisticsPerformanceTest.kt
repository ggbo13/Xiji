package com.xiji.habits.domain

import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSpec
import com.xiji.habits.domain.model.WeekStart
import com.xiji.habits.domain.statistics.StatisticsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatisticsPerformanceTest {
    @Test
    fun `overview scans history a bounded number of times as habit count grows`() {
        val today = LocalDate.of(2026, 3, 9)
        val habits = (1L..20L).map { id ->
            Habit(
                id = id, name = "习惯$id", description = "", icon = "book", color = 0,
                categoryId = null, type = HabitType.COMPLETE, targetValue = 1.0, targetUnit = "",
                schedule = ScheduleSpec.daily(), startDate = today.minusDays(30),
                sortOrder = id.toInt(), archivedAt = null, confirmQuitDaily = false,
                createdAt = 0, updatedAt = 0
            )
        }
        val history = habits.flatMap { habit ->
            (1L..30L).map { daysAgo ->
                HabitRecord(0, habit.id, today.minusDays(daysAgo), RecordStatus.DONE, 1.0, null, 0, 0)
            }
        }
        val records = CountingRecords(history)

        val overview = StatisticsEngine.overview(
            habits, emptyMap(), emptyList(), records, today, WeekStart.MONDAY
        )

        assertEquals(600, overview.totalCompletions)
        assertEquals(30, overview.totalDays)
        assertEquals(30, overview.currentBestStreak)
        // 用访问次数检验复杂度，避免计时断言受电脑速度与构建负载影响。
        assertTrue("总览不应对每个习惯重新扫描全表，实际读取 ${records.reads} 次", records.reads <= history.size * 8)
        println("统计性能样本：${habits.size} 个习惯，${history.size} 条记录，全表读取 ${records.reads} 次")
    }

    private class CountingRecords(private val records: List<HabitRecord>) : AbstractList<HabitRecord>() {
        var reads = 0
            private set

        override val size: Int get() = records.size

        override fun get(index: Int): HabitRecord {
            reads++
            return records[index]
        }
    }
}

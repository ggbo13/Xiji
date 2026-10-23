package com.xiji.habits.domain.schedule

import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitPausePeriod
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSnapshot
import com.xiji.habits.domain.model.ScheduleType
import com.xiji.habits.domain.model.WeekStart
import com.xiji.habits.domain.model.startOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 计划与打卡状态的唯一判定入口。
 *
 * 设计要点：
 * 1. 计划判定依赖「生效于该日期的快照」，因此修改周期不会改写历史。
 * 2. 未来日期永远不可标记完成。
 * 3. 暂停区间不计入分母。
 * 4. 每周 N 次以「自然周」为结算单位，未结束的周不提前判负。
 */
object ScheduleEngine {

    /**
     * 取某天生效的计划快照：effectiveFrom <= date 中最大的一个。
     *
     * 该函数在逐日循环中被调用（连续计算、完成率、热力图），
     * 因此先按 habitId 过滤再比较，避免每次都复制整个快照列表。
     * 调用方若已知快照已按 habit 分组，可直接传子列表。
     */
    fun snapshotFor(
        snapshots: List<ScheduleSnapshot>,
        habit: Habit,
        date: LocalDate
    ): ScheduleSnapshot? {
        var best: ScheduleSnapshot? = null
        for (snap in snapshots) {
            if (snap.habitId != habit.id) continue
            if (snap.effectiveFrom.isAfter(date)) continue
            if (best == null || snap.effectiveFrom.isAfter(best.effectiveFrom)) best = snap
        }
        return best
    }

    /** 暂停判定：只遍历该习惯的暂停区间，避免全表扫描 */
    fun isPaused(pauses: List<HabitPausePeriod>, habitId: Long, date: LocalDate): Boolean {
        for (p in pauses) {
            if (p.habitId != habitId) continue
            if (date.isBefore(p.startDate)) continue
            if (p.endDate == null || !date.isAfter(p.endDate)) return true
        }
        return false
    }

    /** 该日期是否属于计划（未考虑暂停与开始日期之外的语义） */
    fun isScheduledOn(
        habit: Habit,
        snapshots: List<ScheduleSnapshot>,
        date: LocalDate
    ): Boolean {
        if (date.isBefore(habit.startDate)) return false
        val snap = snapshotFor(snapshots, habit, date)
        val spec = snap?.schedule ?: habit.schedule
        return spec.isScheduled(date)
    }

    /**
     * 计算某天的展示状态。
     * @param today 由调用方注入，便于测试与「修改系统日期」场景。
     */
    fun dayState(
        habit: Habit,
        snapshots: List<ScheduleSnapshot>,
        pauses: List<HabitPausePeriod>,
        record: HabitRecord?,
        date: LocalDate,
        today: LocalDate
    ): DayState {
        if (date.isAfter(today)) return DayState.FUTURE
        if (date.isBefore(habit.startDate)) return DayState.NOT_SCHEDULED
        if (isPaused(pauses, habit.id, date)) return DayState.PAUSED

        val scheduled = isScheduledOn(habit, snapshots, date)
        if (!scheduled) return DayState.NOT_SCHEDULED

        // 戒除型：未主动记录 ≠ 成功
        return when (record?.status) {
            RecordStatus.DONE -> DayState.DONE
            RecordStatus.FAILED -> DayState.FAILED
            RecordStatus.SKIPPED -> DayState.SKIPPED
            null -> if (habit.isQuantity && (record?.accumulatedValue ?: 0.0) > 0.0) {
                DayState.PARTIAL
            } else DayState.PENDING
        }
    }

    /** 是否为「应完成」的一天（用于分母）：计划内、非暂停、非未来、已开始 */
    fun isCountableDay(
        habit: Habit,
        snapshots: List<ScheduleSnapshot>,
        pauses: List<HabitPausePeriod>,
        date: LocalDate,
        today: LocalDate
    ): Boolean {
        if (date.isAfter(today)) return false
        if (date.isBefore(habit.startDate)) return false
        if (isPaused(pauses, habit.id, date)) return false
        return isScheduledOn(habit, snapshots, date)
    }

    /** 数量型是否达标 */
    fun isQuantityReached(habit: Habit, snapshot: ScheduleSnapshot?, record: HabitRecord?): Boolean {
        if (record == null) return false
        if (record.status == RecordStatus.DONE) return true
        val target = snapshot?.targetValue ?: habit.targetValue
        return target > 0 && record.accumulatedValue >= target
    }

    /**
     * 连续坚持计算。
     *
     * 语义：
     * - 按「周期」推进，而不是按自然日推进。每日/隔天/指定星期按天推进，
     *   每周 N 次按自然周推进。
     * - 每周 N 次：某周内完成次数达到 N 即算成功；当前周未结束且未达标不算失败。
     * - 跳过（SKIPPED）与暂停日不打断连续。
     * - 返回 (当前连续, 最长连续)，单位为天或周。
     */
    fun streak(
        habit: Habit,
        snapshots: List<ScheduleSnapshot>,
        pauses: List<HabitPausePeriod>,
        records: List<HabitRecord>,
        today: LocalDate,
        weekStart: WeekStart = WeekStart.MONDAY
    ): StreakResult {
        val recordByDate = records.filter { it.habitId == habit.id }.associateBy { it.date }

        return if (habit.schedule.type == ScheduleType.TIMES_PER_WEEK ||
            snapshotFor(snapshots, habit, today)?.schedule?.type == ScheduleType.TIMES_PER_WEEK
        ) {
            weeklyStreak(habit, snapshots, pauses, recordByDate, today, weekStart)
        } else {
            dailyStreak(habit, snapshots, pauses, recordByDate, today)
        }
    }

    private fun isSuccess(
        habit: Habit,
        snapshots: List<ScheduleSnapshot>,
        record: HabitRecord?
    ): Boolean {
        if (record == null) return false
        return when (record.status) {
            RecordStatus.DONE -> true
            RecordStatus.SKIPPED -> true // 主动跳过不打断连续
            RecordStatus.FAILED -> false
        }
    }

    /**
     * 逐日推进的连续计算（每日 / 指定星期 / 每隔 N 天）。
     *
     * - 只统计「可计数日」（计划内、非暂停、已开始、非未来）。
     * - 今天若尚未记录，不视为中断，也不计入连续。
     * - 跳过与暂停不打断连续。
     */
    private fun dailyStreak(
        habit: Habit,
        snapshots: List<ScheduleSnapshot>,
        pauses: List<HabitPausePeriod>,
        recordByDate: Map<LocalDate, HabitRecord>,
        today: LocalDate
    ): StreakResult {
        // 从今天向前收集可计数日的成败序列（新 -> 旧）
        val days = ArrayList<Boolean>()
        var cursor = today
        var guard = 0
        while (!cursor.isBefore(habit.startDate) && guard++ < 3650) {
            if (isCountableDay(habit, snapshots, pauses, cursor, today)) {
                val rec = recordByDate[cursor]
                val success = isSuccess(habit, snapshots, rec)
                val pendingToday = cursor == today && rec == null
                if (!pendingToday) days.add(success)
            }
            cursor = cursor.minusDays(1)
        }

        var longest = 0
        var run = 0
        for (ok in days) {
            if (ok) { run++; if (run > longest) longest = run } else run = 0
        }
        var current = 0
        for (ok in days) {
            if (ok) current++ else break
        }
        return StreakResult(current, longest, StreakUnitDays)
    }

    /**
     * 按自然周结算的连续（每周 N 次）。
     *
     * - 某周完成次数 >= N 视为成功；整周暂停忽略。
     * - 当前周未结束且未达标时，不判定为失败，也不计入连续。
     */
    private fun weeklyStreak(
        habit: Habit,
        snapshots: List<ScheduleSnapshot>,
        pauses: List<HabitPausePeriod>,
        recordByDate: Map<LocalDate, HabitRecord>,
        today: LocalDate,
        weekStart: WeekStart
    ): StreakResult {
        var week = today.startOfWeek(weekStart)
        val startWeek = habit.startDate.startOfWeek(weekStart)
        val currentWeek = week
        val results = ArrayList<Boolean>()
        var guard = 0
        while (!week.isBefore(startWeek) && guard++ < 600) {
            val spec = snapshotFor(snapshots, habit, week.plusDays(3))?.schedule ?: habit.schedule
            val target = if (spec.type == ScheduleType.TIMES_PER_WEEK) spec.timesPerWeek else 7
            var done = 0
            var pausedDays = 0
            var activeDays = 0
            for (i in 0..6) {
                val d = week.plusDays(i.toLong())
                if (d.isAfter(today)) break
                if (d.isBefore(habit.startDate)) continue
                if (isPaused(pauses, habit.id, d)) { pausedDays++; continue }
                activeDays++
                if (isSuccess(habit, snapshots, recordByDate[d])) done++
            }
            val isCurrentWeek = week == currentWeek
            when {
                activeDays == 0 -> Unit // 整周暂停或尚未开始，忽略
                done >= target -> results.add(true)
                isCurrentWeek -> Unit // 进行中的周不判定
                else -> results.add(false)
            }
            week = week.minusWeeks(1)
        }

        var longest = 0
        var run = 0
        for (ok in results) { if (ok) { run++; if (run > longest) longest = run } else run = 0 }
        var current = 0
        for (ok in results) { if (ok) current++ else break }
        return StreakResult(current, longest, StreakUnitWeeks)
    }
}

const val StreakUnitDays = "天"
const val StreakUnitWeeks = "周"

data class StreakResult(val current: Int, val longest: Int, val unit: String)

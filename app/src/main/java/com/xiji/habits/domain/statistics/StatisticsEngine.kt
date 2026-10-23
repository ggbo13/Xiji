package com.xiji.habits.domain.statistics

import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitPausePeriod
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSnapshot
import com.xiji.habits.domain.model.ScheduleType
import com.xiji.habits.domain.model.WeekStart
import com.xiji.habits.domain.model.startOfWeek
import com.xiji.habits.domain.schedule.ScheduleEngine
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 统计口径（详见 DATA_MODEL.md）：
 * - 分母只包含「有效计划日」：计划内、已开始、非暂停、非未来。
 * - 跳过（SKIPPED）既不计入分子也不计入分母。
 * - 数量型按是否达标计入分子，同日多次累加不重复计数。
 * - 当前未结束的周/月不计入完成率分母，避免提前判负。
 * - 戒除型未记录 = 未完成，不算成功。
 */
object StatisticsEngine {

    data class DayAggregate(
        val date: LocalDate,
        val scheduled: Int,
        val done: Int,
        val failed: Int,
        val skipped: Int,
        val pending: Int,
        val paused: Boolean
    ) {
        val ratio: Float get() = if (scheduled - skipped <= 0) 0f else done.toFloat() / (scheduled - skipped)
        val hasData: Boolean get() = done > 0 || failed > 0 || skipped > 0
    }

    data class HabitStats(
        val habitId: Long,
        val totalCompletions: Int,
        val totalDays: Int,
        val currentStreak: Int,
        val longestStreak: Int,
        val streakUnit: String,
        val weekRate: Float,
        val monthRate: Float,
        val overallRate: Float,
        val strength: Int,
        val weekDone: Int,
        val weekTarget: Int,
        val monthDone: Int,
        val monthTarget: Int
    )

    fun countStatus(record: HabitRecord?, habit: Habit, snapshot: ScheduleSnapshot?): Boolean {
        if (record == null) return false
        return when (record.status) {
            RecordStatus.DONE -> true
            RecordStatus.FAILED -> false
            RecordStatus.SKIPPED -> false
        }
    }

    /**
     * 轻量统计：只计算列表卡片需要的「当前连续」与「本周配额」。
     *
     * 为什么需要它：列表页曾经对每个习惯调用 [habitStats]，而后者为了算累计完成率
     * 会从 startDate 逐日扫描到今天。100 个习惯 × 2 年历史 ≈ 7 万次迭代 × 2，
     * 每次数据变化都重算，这是卡顿的主因。列表页并不需要累计完成率，
     * 因此单独提供一个 O(窗口) 的轻量版本。
     */
    fun listStats(
        habit: Habit,
        snapshots: List<ScheduleSnapshot>,
        pauses: List<HabitPausePeriod>,
        records: List<HabitRecord>,
        today: LocalDate,
        weekStart: WeekStart = WeekStart.MONDAY
    ): ListStats {
        val streak = ScheduleEngine.streak(habit, snapshots, pauses, records, today, weekStart)
        val spec = ScheduleEngine.snapshotFor(snapshots, habit, today)?.schedule ?: habit.schedule
        val byDate = records.asSequence().filter { it.habitId == habit.id }.associateBy { it.date }
        val weekStartDate = today.startOfWeek(weekStart)
        var weekDone = 0
        var elapsed = 0
        var wd = weekStartDate
        while (!wd.isAfter(today)) {
            if (ScheduleEngine.isCountableDay(habit, snapshots, pauses, wd, today)) {
                val rec = byDate[wd]
                if (rec?.status != RecordStatus.SKIPPED) {
                    elapsed++
                    val snap = ScheduleEngine.snapshotFor(snapshots, habit, wd)
                    if (countStatus(rec, habit, snap)) weekDone++
                }
            }
            wd = wd.plusDays(1)
        }
        val weekTarget = if (spec.type == ScheduleType.TIMES_PER_WEEK) spec.timesPerWeek else elapsed
        return ListStats(
            currentStreak = streak.current,
            longestStreak = streak.longest,
            streakUnit = streak.unit,
            weekDone = weekDone,
            weekTarget = weekTarget
        )
    }

    data class ListStats(
        val currentStreak: Int,
        val longestStreak: Int,
        val streakUnit: String,
        val weekDone: Int,
        val weekTarget: Int
    )

    /** 单习惯统计 */
    fun habitStats(
        habit: Habit,
        snapshots: List<ScheduleSnapshot>,
        pauses: List<HabitPausePeriod>,
        records: List<HabitRecord>,
        today: LocalDate,
        weekStart: WeekStart = WeekStart.MONDAY
    ): HabitStats {
        val byDate = records.filter { it.habitId == habit.id }.associateBy { it.date }
        val streak = ScheduleEngine.streak(habit, snapshots, pauses, records, today, weekStart)

        // 累计完成次数：每一条有效完成记录算一次（数量型同一天累加只算一次）
        var totalCompletions = 0
        var totalDays = 0
        var overallNumerator = 0
        var overallDenominator = 0
        val start = habit.startDate
        // 今天尚未结束，不计入历史完成率的分母（避免提前判负）
        val lastClosedDay = today.minusDays(1)
        var d = start
        var guard = 0
        while (!d.isAfter(lastClosedDay) && guard++ < 36500) {
            val counted = ScheduleEngine.isCountableDay(habit, snapshots, pauses, d, today)
            if (counted) {
                val rec = byDate[d]
                val snap = ScheduleEngine.snapshotFor(snapshots, habit, d)
                when (rec?.status) {
                    RecordStatus.SKIPPED -> Unit
                    else -> {
                        overallDenominator++
                        if (countStatus(rec, habit, snap)) {
                            overallNumerator++
                            totalCompletions++
                            totalDays++
                        }
                    }
                }
            }
            d = d.plusDays(1)
        }

        // 本周：配额型按「周目标」结算，其余按已过去的计划日结算。
        val spec = ScheduleEngine.snapshotFor(snapshots, habit, today)?.schedule ?: habit.schedule
        val weekStartDate = today.startOfWeek(weekStart)
        var weekDone = 0
        var elapsedScheduled = 0
        var wd = weekStartDate
        while (!wd.isAfter(today)) {
            if (ScheduleEngine.isCountableDay(habit, snapshots, pauses, wd, today)) {
                val rec = byDate[wd]
                if (rec?.status != RecordStatus.SKIPPED) {
                    elapsedScheduled++
                    val snap = ScheduleEngine.snapshotFor(snapshots, habit, wd)
                    if (countStatus(rec, habit, snap)) weekDone++
                }
            }
            wd = wd.plusDays(1)
        }
        val weekTarget = if (spec.type == ScheduleType.TIMES_PER_WEEK) spec.timesPerWeek else elapsedScheduled

        // 本月：配额型按「本月已过周数 × 每周目标」结算。
        val monthStart = today.withDayOfMonth(1)
        var monthDone = 0
        var monthTarget = 0
        var md = monthStart
        while (!md.isAfter(lastClosedDay)) {
            if (ScheduleEngine.isCountableDay(habit, snapshots, pauses, md, today)) {
                val rec = byDate[md]
                if (rec?.status != RecordStatus.SKIPPED) {
                    if (spec.type != ScheduleType.TIMES_PER_WEEK) monthTarget++
                    val snap = ScheduleEngine.snapshotFor(snapshots, habit, md)
                    if (countStatus(rec, habit, snap)) monthDone++
                }
            }
            md = md.plusDays(1)
        }
        if (spec.type == ScheduleType.TIMES_PER_WEEK) {
            val weeks = (0 until 6).map { monthStart.plusWeeks(it.toLong()) }
                .count { it.isBefore(today.plusDays(1)) && !it.plusDays(6).isBefore(monthStart) }
            monthTarget = weeks * spec.timesPerWeek
        }

        val weekRate = if (weekTarget == 0) 0f else weekDone.toFloat() / weekTarget
        val monthRate = if (monthTarget == 0) 0f else monthDone.toFloat() / monthTarget
        val overallRate = if (overallDenominator == 0) 0f else overallNumerator.toFloat() / overallDenominator

        return HabitStats(
            habitId = habit.id,
            totalCompletions = totalCompletions,
            totalDays = totalDays,
            currentStreak = streak.current,
            longestStreak = streak.longest,
            streakUnit = streak.unit,
            weekRate = weekRate,
            monthRate = monthRate,
            overallRate = overallRate,
            strength = strength(habit, snapshots, pauses, records, today),
            weekDone = weekDone,
            weekTarget = weekTarget,
            monthDone = monthDone,
            monthTarget = monthTarget
        )
    }

    /**
     * 习迹强度（0-100）。
     *
     * 独立的透明规则（非 Loop 的递归 EMA）：
     *   在最近 [WINDOW_DAYS] 个自然日内，对每个「有效计划日」按距今时间加权，
     *   w = DECAY^daysAgo；强度 = 100 × Σ(w·成功) / Σ(w)。
     *   跳过与暂停日不参与计算；数量型达标即成功。
     *
     * 特性：
     * - 偶尔遗漏一次不会清零，只会小幅降低；
     * - 长期停更会自然衰减（因为旧的成功权重随时间降低）；
     * - 与「连续天数」不同：连续为 0 时强度仍可能较高。
     */
    const val STRENGTH_WINDOW_DAYS = 90
    const val STRENGTH_DECAY = 0.96

    fun strength(
        habit: Habit,
        snapshots: List<ScheduleSnapshot>,
        pauses: List<HabitPausePeriod>,
        records: List<HabitRecord>,
        today: LocalDate
    ): Int {
        val byDate = records.filter { it.habitId == habit.id }.associateBy { it.date }
        var weightedSum = 0.0
        var weightTotal = 0.0
        for (i in 0 until STRENGTH_WINDOW_DAYS) {
            val d = today.minusDays(i.toLong())
            if (d.isBefore(habit.startDate)) break
            if (!ScheduleEngine.isCountableDay(habit, snapshots, pauses, d, today)) continue
            val rec = byDate[d]
            if (rec?.status == RecordStatus.SKIPPED) continue
            val w = STRENGTH_DECAY.pow(i.toDouble())
            val snap = ScheduleEngine.snapshotFor(snapshots, habit, d)
            if (countStatus(rec, habit, snap)) weightedSum += w
            weightTotal += w
        }
        if (weightTotal <= 0.0) return 0
        return (100.0 * weightedSum / weightTotal).roundToInt().coerceIn(0, 100)
    }

    /** 热力图：最近 N 天，每天聚合所有（或指定）习惯 */
    fun heatmap(
        habits: List<Habit>,
        snapshots: Map<Long, List<ScheduleSnapshot>>,
        pauses: List<HabitPausePeriod>,
        records: List<HabitRecord>,
        start: LocalDate,
        end: LocalDate,
        today: LocalDate
    ): List<DayAggregate> {
        val byHabitDate = records.groupBy { it.habitId to it.date }
        val result = ArrayList<DayAggregate>()
        var d = start
        while (!d.isAfter(end)) {
            var scheduled = 0; var done = 0; var failed = 0; var skipped = 0; var pending = 0
            var allPaused = false
            for (h in habits) {
                val snaps = snapshots[h.id].orEmpty()
                val paused = ScheduleEngine.isPaused(pauses, h.id, d)
                val state = ScheduleEngine.dayState(h, snaps, pauses, byHabitDate[h.id to d]?.firstOrNull(), d, today)
                when (state) {
                    DayState.DONE -> { scheduled++; done++ }
                    DayState.FAILED -> { scheduled++; failed++ }
                    DayState.SKIPPED -> { scheduled++; skipped++ }
                    DayState.PENDING -> { scheduled++; pending++ }
                    DayState.PARTIAL -> { scheduled++; pending++ }
                    DayState.PAUSED -> { allPaused = allPaused || false }
                    else -> Unit
                }
            }
            result.add(DayAggregate(d, scheduled, done, failed, skipped, pending, allPaused))
            d = d.plusDays(1)
        }
        return result
    }

    /** 趋势：按天统计完成率 */
    fun trend(
        habits: List<Habit>,
        snapshots: Map<Long, List<ScheduleSnapshot>>,
        pauses: List<HabitPausePeriod>,
        records: List<HabitRecord>,
        start: LocalDate,
        end: LocalDate,
        today: LocalDate
    ): List<DayAggregate> = heatmap(habits, snapshots, pauses, records, start, end, today)

    /** 日详情：某天所有应执行的习惯及其状态 */
    data class DayEntry(
        val habit: Habit,
        val record: HabitRecord?,
        val state: DayState,
        val targetValue: Double
    )

    fun dayEntries(
        habits: List<Habit>,
        snapshots: Map<Long, List<ScheduleSnapshot>>,
        pauses: List<HabitPausePeriod>,
        records: List<HabitRecord>,
        date: LocalDate,
        today: LocalDate
    ): List<DayEntry> {
        val byHabitDate = records.groupBy { it.habitId to it.date }
        return habits.mapNotNull { h ->
            val rec = byHabitDate[h.id to date]?.firstOrNull()
            val state = ScheduleEngine.dayState(h, snapshots[h.id].orEmpty(), pauses, rec, date, today)
            if (state == DayState.NOT_SCHEDULED) null
            else DayEntry(h, rec, state, snapshots[h.id].orEmpty().let { s ->
                ScheduleEngine.snapshotFor(s, h, date)?.targetValue ?: h.targetValue
            })
        }
    }

    fun dayRate(entries: List<DayEntry>): Float {
        val valid = entries.filter { it.state != DayState.SKIPPED && it.state != DayState.PAUSED }
        if (valid.isEmpty()) return 0f
        return valid.count { it.state == DayState.DONE }.toFloat() / valid.size
    }

    /** 跨习惯总览 */
    data class Overview(
        val totalCompletions: Int,
        val totalDays: Int,
        val currentBestStreak: Int,
        val currentBestUnit: String,
        val weekRate: Float,
        val monthRate: Float,
        val last30Trend: List<DayAggregate>
    )

    fun overview(
        habits: List<Habit>,
        snapshots: Map<Long, List<ScheduleSnapshot>>,
        pauses: List<HabitPausePeriod>,
        records: List<HabitRecord>,
        today: LocalDate,
        weekStart: WeekStart,
        precomputedStats: List<HabitStats>? = null
    ): Overview {
        if (habits.isEmpty()) {
            return Overview(0, 0, 0, "天", 0f, 0f, emptyList())
        }
        // 每个习惯只遍历自己的历史；统计页已有同批结果时直接复用。
        val stats = precomputedStats ?: run {
            val recordsByHabit = records.groupBy { it.habitId }
            val pausesByHabit = pauses.groupBy { it.habitId }
            habits.map { habitStats(it, snapshots[it.id].orEmpty(), pausesByHabit[it.id].orEmpty(), recordsByHabit[it.id].orEmpty(), today, weekStart) }
        }
        val best = stats.maxByOrNull { it.currentStreak }
        return Overview(
            totalCompletions = stats.sumOf { it.totalCompletions },
            totalDays = heatmap(
                habits, snapshots, pauses, records,
                today.minusDays(364), today, today
            ).count { it.done > 0 },
            currentBestStreak = best?.currentStreak ?: 0,
            currentBestUnit = best?.streakUnit ?: "天",
            weekRate = averageRate(stats.map { it.weekRate }),
            monthRate = averageRate(stats.map { it.monthRate }),
            last30Trend = trend(
                habits, snapshots, pauses, records,
                today.minusDays(29), today, today
            )
        )
    }

    private fun averageRate(list: List<Float>): Float =
        if (list.isEmpty()) 0f else list.average().toFloat()

    fun daysBetweenInclusive(a: LocalDate, b: LocalDate): Long =
        ChronoUnit.DAYS.between(a, b) + 1
}

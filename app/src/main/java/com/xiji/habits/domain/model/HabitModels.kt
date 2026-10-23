package com.xiji.habits.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 习惯类型：完成型 / 数量型 / 戒除型 */
enum class HabitType { COMPLETE, QUANTITY, QUIT }

/** 周期类型 */
enum class ScheduleType { DAILY, WEEKDAYS, TIMES_PER_WEEK, INTERVAL }

/** 记录状态（持久化到数据库） */
enum class RecordStatus { DONE, FAILED, SKIPPED }

/** 某一天在 UI 上呈现的状态（由计划 + 记录 + 暂停推导，不落库） */
enum class DayState {
    NOT_SCHEDULED, // 非计划日期
    PAUSED,        // 暂停期间
    PENDING,       // 计划内，尚未记录
    PARTIAL,       // 数量型：有进度但未达标
    DONE,
    FAILED,
    SKIPPED,
    FUTURE         // 未来日期，不可提前记录
}

enum class StreakUnit { DAYS, WEEKS }

/** 周期定义。与 UI 完全解耦，可单独测试。 */
data class ScheduleSpec(
    val type: ScheduleType,
    /** 周几生效，bit0 = 周一 ... bit6 = 周日 */
    val weekdaysMask: Int = ALL_DAYS,
    val timesPerWeek: Int = 3,
    val intervalDays: Int = 2,
    val anchorDate: LocalDate = LocalDate.of(1970, 1, 1)
) {
    companion object {
        const val ALL_DAYS = 0b1111111

        fun daily() = ScheduleSpec(ScheduleType.DAILY)
        fun weekdays(mask: Int) = ScheduleSpec(ScheduleType.WEEKDAYS, weekdaysMask = mask)
        fun timesPerWeek(n: Int) = ScheduleSpec(ScheduleType.TIMES_PER_WEEK, timesPerWeek = n.coerceIn(1, 7))
        fun interval(days: Int, anchor: LocalDate) =
            ScheduleSpec(ScheduleType.INTERVAL, intervalDays = days.coerceAtLeast(1), anchorDate = anchor)
    }

    val isQuotaBased: Boolean get() = type == ScheduleType.TIMES_PER_WEEK

    /** 该日期是否落在计划内（不判断完成与否） */
    fun isScheduled(date: LocalDate): Boolean = when (type) {
        ScheduleType.DAILY -> true
        ScheduleType.WEEKDAYS -> (weekdaysMask shr date.dayOfWeek.index0) and 1 == 1
        ScheduleType.INTERVAL -> {
            if (date.isBefore(anchorDate)) false
            else ChronoUnit.DAYS.between(anchorDate, date) % intervalDays == 0L
        }
        // 每周 N 次：每天都可作为执行日，按周结算配额
        ScheduleType.TIMES_PER_WEEK -> true
    }

    fun weekdayEnabled(day: DayOfWeek): Boolean = (weekdaysMask shr day.index0) and 1 == 1

    /** 人类可读描述，供列表与详情展示 */
    fun describe(weekStartsOnMonday: Boolean = true): String = when (type) {
        ScheduleType.DAILY -> "每天"
        ScheduleType.WEEKDAYS -> {
            val names = listOf("一", "二", "三", "四", "五", "六", "日")
            val picked = (0..6).filter { (weekdaysMask shr it) and 1 == 1 }
            when {
                picked.size == 7 -> "每天"
                picked.size == 5 && picked.all { it < 5 } -> "工作日"
                picked.size == 2 && picked.containsAll(listOf(5, 6)) -> "周末"
                picked.isEmpty() -> "未设置"
                else -> "每周" + picked.joinToString("、") { names[it] }
            }
        }
        ScheduleType.TIMES_PER_WEEK -> "每周 $timesPerWeek 次"
        ScheduleType.INTERVAL -> if (intervalDays == 1) "每天" else "每隔 $intervalDays 天"
    }
}

val DayOfWeek.index0: Int get() = value - 1

/** 周起始日设置 */
enum class WeekStart { MONDAY, SUNDAY }

fun LocalDate.startOfWeek(start: WeekStart): LocalDate {
    val shift = if (start == WeekStart.MONDAY) dayOfWeek.index0 else (dayOfWeek.index0 + 1) % 7
    return minusDays(shift.toLong())
}

fun LocalDate.endOfWeek(start: WeekStart): LocalDate = startOfWeek(start).plusDays(6)

/** 习惯的领域模型 */
data class Habit(
    val id: Long,
    val name: String,
    val description: String,
    val icon: String,
    val color: Long,
    val categoryId: Long?,
    val type: HabitType,
    val targetValue: Double,
    val targetUnit: String,
    val schedule: ScheduleSpec,
    val startDate: LocalDate,
    val sortOrder: Int,
    val archivedAt: Long?,
    val confirmQuitDaily: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    val isArchived: Boolean get() = archivedAt != null
    val isQuantity: Boolean get() = type == HabitType.QUANTITY
    val isQuit: Boolean get() = type == HabitType.QUIT

    /** 目标文案，例如「阅读 30 分钟」下方显示「每天 30 分钟」 */
    fun targetLabel(): String = when (type) {
        HabitType.COMPLETE -> schedule.describe()
        HabitType.QUANTITY -> "${schedule.describe()} · 目标 ${formatValue(targetValue)}${targetUnit}"
        HabitType.QUIT -> "${schedule.describe()} · 保持克制"
    }
}

fun formatValue(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)

/** 单日打卡记录 */
data class HabitRecord(
    val id: Long,
    val habitId: Long,
    val date: LocalDate,
    val status: RecordStatus,
    val accumulatedValue: Double,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** 事后补记。补签只是把已经发生的事记下来，不提供任何额外奖励。 */
    val isBackfilled: Boolean = false
)

data class HabitCategory(
    val id: Long,
    val name: String,
    val color: Long,
    val sortOrder: Int
)

data class HabitReminder(
    val id: Long,
    val habitId: Long,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val weekdaysMask: Int
)

data class HabitPausePeriod(
    val id: Long,
    val habitId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val reason: String?
)

/** 计划快照：修改周期/目标时追加，历史统计使用当时生效的规则 */
data class ScheduleSnapshot(
    val id: Long,
    val habitId: Long,
    val effectiveFrom: LocalDate,
    val schedule: ScheduleSpec,
    val targetValue: Double,
    val targetUnit: String,
    val createdAt: Long
)

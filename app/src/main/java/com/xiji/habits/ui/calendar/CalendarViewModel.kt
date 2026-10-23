package com.xiji.habits.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiji.habits.data.repository.HabitRepository
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitPausePeriod
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSnapshot
import com.xiji.habits.domain.schedule.ScheduleEngine
import com.xiji.habits.domain.statistics.StatisticsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CalendarDayEntry(
    val habit: Habit,
    val record: HabitRecord?,
    val state: DayState,
    val targetValue: Double
)

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val monthState: Map<LocalDate, DayState> = emptyMap(),
    val dayEntries: List<CalendarDayEntry> = emptyList(),
    val dayRate: Float = 0f,
    val monthDone: Int = 0,
    val monthScheduled: Int = 0,
    val snack: String? = null,
    val loading: Boolean = true,
    val backfillMode: Boolean = false
)

class CalendarViewModel(private val repo: HabitRepository) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())
    private val snack = MutableStateFlow<String?>(null)
    private val backfillMode = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            combine(
                month, selected, repo.habits, repo.records, repo.snapshots, repo.pauses, backfillMode
            ) { values ->
                val m = values[0] as YearMonth
                val sel = values[1] as LocalDate
                val habits = values[2] as List<Habit>
                val records = values[3] as List<HabitRecord>
                val snapshots = values[4] as List<ScheduleSnapshot>
                val pauses = values[5] as List<HabitPausePeriod>
                val backfill = values[6] as Boolean
                val today = LocalDate.now()

                val snapsByHabit = snapshots.groupBy { it.habitId }
                val recordsByDate = records.groupBy { it.date }
                val monthDays = (1..m.lengthOfMonth()).map { m.atDay(it) }

                // 每天的聚合状态（用于日历格）
                val monthState = monthDays.associateWith { d ->
                    aggregateState(habits, snapsByHabit, pauses, recordsByDate[d].orEmpty(), d, today)
                }

                val dayEntries = StatisticsEngine.dayEntries(
                    habits, snapsByHabit, pauses, recordsByDate[sel].orEmpty(), sel, today
                )
                val entries = dayEntries.map { CalendarDayEntry(it.habit, it.record, it.state, it.targetValue) }

                val countableDays = monthDays.filter { it.isBefore(today) }
                val scheduledCount = countableDays.sumOf { d ->
                    habits.count { h ->
                        ScheduleEngine.isCountableDay(h, snapsByHabit[h.id].orEmpty(), pauses, d, today)
                    }
                }
                val doneCount = countableDays.sumOf { d ->
                    val dayRecords = recordsByDate[d].orEmpty().associateBy { it.habitId }
                    habits.count { h ->
                        val rec = dayRecords[h.id]
                        rec?.status == RecordStatus.DONE
                    }
                }

                CalendarUiState(
                    month = m,
                    today = today,
                    selectedDate = sel,
                    monthState = monthState,
                    dayEntries = entries,
                    dayRate = StatisticsEngine.dayRate(dayEntries),
                    monthDone = doneCount,
                    monthScheduled = scheduledCount,
                    loading = false,
                    snack = snack.value,
                    backfillMode = backfill
                )
            }.flowOn(Dispatchers.Default).collect { _state.value = it }
        }
    }

    private fun aggregateState(
        habits: List<Habit>,
        snaps: Map<Long, List<ScheduleSnapshot>>,
        pauses: List<HabitPausePeriod>,
        records: List<HabitRecord>,
        date: LocalDate,
        today: LocalDate
    ): DayState {
        val entries = StatisticsEngine.dayEntries(habits, snaps, pauses, records, date, today)
        if (entries.isEmpty()) return DayState.NOT_SCHEDULED
        if (date.isAfter(today)) return DayState.FUTURE
        val valid = entries.filter { it.state != DayState.SKIPPED }
        if (valid.isEmpty()) return DayState.SKIPPED
        val done = valid.count { it.state == DayState.DONE }
        val failed = valid.count { it.state == DayState.FAILED }
        val pending = valid.count { it.state == DayState.PENDING || it.state == DayState.PARTIAL }
        return when {
            done == valid.size -> DayState.DONE
            done > 0 -> DayState.PARTIAL
            failed > 0 -> DayState.FAILED
            pending > 0 && date == today -> DayState.PENDING
            pending > 0 -> DayState.FAILED
            else -> DayState.PENDING
        }
    }

    fun prevMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { it.plusMonths(1) }
    fun selectDate(d: LocalDate) { selected.value = d }

    fun setStatus(habit: Habit, date: LocalDate, status: RecordStatus) {
        if (date.isAfter(LocalDate.now())) return
        val backfill = date.isBefore(LocalDate.now())
        viewModelScope.launch {
            repo.setStatus(habit.id, date, status, backfill = backfill)
            snack.value = if (backfill) "已补签「${habit.name}」" else "已更新记录"
        }
    }

    /**
     * 补签：把某个「今天以前」的日期补记为已完成。
     * 只记录事实，不产生额外奖励；未来日期与暂停日会被拒绝。
     */
    fun backfill(habit: Habit, date: LocalDate, note: String? = null) {
        val today = LocalDate.now()
        if (!date.isBefore(today)) {
            snack.value = "只能补签今天以前的日期"
            return
        }
        viewModelScope.launch {
            val paused = repo.allPausesOnce().any { p ->
                p.habitId == habit.id && !date.isBefore(p.startDate) &&
                    (p.endDate == null || !date.isAfter(p.endDate))
            }
            if (paused) {
                snack.value = "该日期处于暂停期间，无需补签"
                return@launch
            }
            repo.setStatus(habit.id, date, RecordStatus.DONE, note = note, backfill = true)
            snack.value = "已补签「${habit.name}」"
        }
    }

    /** 批量补签：把某天所有未记录的习惯一次性补记为完成 */
    fun backfillAllMissing(date: LocalDate) {
        val today = LocalDate.now()
        if (!date.isBefore(today)) {
            snack.value = "只能补签今天以前的日期"
            return
        }
        viewModelScope.launch {
            val pauses = repo.allPausesOnce()
            val targets = _state.value.dayEntries.filter { e ->
                (e.state == DayState.PENDING || e.state == DayState.PARTIAL || e.state == DayState.FAILED) &&
                    pauses.none { p ->
                        p.habitId == e.habit.id && !date.isBefore(p.startDate) &&
                            (p.endDate == null || !date.isAfter(p.endDate))
                    }
            }
            if (targets.isEmpty()) {
                snack.value = "这一天没有需要补签的习惯"
                return@launch
            }
            targets.forEach { repo.setStatus(it.habit.id, date, RecordStatus.DONE, backfill = true) }
            snack.value = "已补签 ${targets.size} 个习惯"
        }
    }

    fun clear(habit: Habit, date: LocalDate) {
        viewModelScope.launch {
            repo.clearRecord(habit.id, date)
            snack.value = "已清除记录"
        }
    }

    fun addValue(habit: Habit, date: LocalDate, delta: Double) {
        viewModelScope.launch { repo.addValue(habit.id, date, delta) }
    }

    fun toggleBackfillMode() { backfillMode.value = !backfillMode.value }
    fun consumeSnack() { snack.value = null }
}

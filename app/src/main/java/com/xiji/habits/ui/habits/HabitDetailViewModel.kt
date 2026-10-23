package com.xiji.habits.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiji.habits.data.preferences.SettingsRepository
import com.xiji.habits.data.preferences.WeekStartSetting
import com.xiji.habits.data.repository.HabitRepository
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitPausePeriod
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSnapshot
import com.xiji.habits.domain.model.WeekStart
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

data class DayDetailItem(
    val habit: Habit,
    val record: HabitRecord?,
    val state: DayState,
    val targetValue: Double
)

data class DetailUiState(
    val habit: Habit? = null,
    val stats: StatisticsEngine.HabitStats? = null,
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = null,
    val selectedItems: List<DayDetailItem> = emptyList(),
    val heatmap: List<StatisticsEngine.DayAggregate> = emptyList(),
    val trend: List<StatisticsEngine.DayAggregate> = emptyList(),
    val history: List<HabitRecord> = emptyList(),
    val monthState: Map<LocalDate, DayState> = emptyMap(),
    val paused: Boolean = false,
    val loading: Boolean = true,
    val snack: String? = null,
    val showDeleteConfirm: Boolean = false,
    val monthRate: Float = 0f
)

class HabitDetailViewModel(
    private val repo: HabitRepository,
    private val settingsRepo: SettingsRepository,
    private val habitId: Long
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow<LocalDate?>(null)
    private val snack = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            combine(
                month,
                selected,
                repo.observeHabit(habitId),
                repo.observeRecords(habitId),
                repo.snapshots,
                repo.pauses,
                settingsRepo.settings
            ) { values ->
                val m = values[0] as YearMonth
                val sel = values[1] as LocalDate?
                val habit = values[2] as Habit?
                val records = values[3] as List<HabitRecord>
                val snapshots = values[4] as List<ScheduleSnapshot>
                val pauses = values[5] as List<HabitPausePeriod>
                val wsSetting = (values[6] as com.xiji.habits.data.preferences.UserSettings).weekStart
                if (habit == null) return@combine DetailUiState(loading = false)

                val today = LocalDate.now()
                val ws = if (wsSetting == WeekStartSetting.MONDAY) WeekStart.MONDAY else WeekStart.SUNDAY
                val snaps = snapshots.filter { it.habitId == habitId }
                val byDate = records.filter { it.habitId == habitId }.associateBy { it.date }
                val stats = StatisticsEngine.habitStats(habit, snaps, pauses, records, today, ws)

                val monthDays = (1..m.lengthOfMonth()).map { m.atDay(it) }
                val monthStates = monthDays.associateWith { d ->
                    ScheduleEngine.dayState(habit, snaps, pauses, byDate[d], d, today)
                }

                val monthCountable = monthDays.filter { d ->
                    ScheduleEngine.isCountableDay(habit, snaps, pauses, d, today) && d != today
                }
                val monthDone = monthCountable.count { byDate[it]?.status == RecordStatus.DONE }
                val monthRate = if (monthCountable.isEmpty()) 0f else monthDone.toFloat() / monthCountable.size

                val heatStart = today.minusDays(363)
                val heatmap = StatisticsEngine.heatmap(
                    listOf(habit), mapOf(habitId to snaps), pauses, records, heatStart, today, today
                )
                val trend = StatisticsEngine.trend(
                    listOf(habit), mapOf(habitId to snaps), pauses, records,
                    today.minusDays(29), today, today
                )

                val items = sel?.let { d ->
                    StatisticsEngine.dayEntries(
                        listOf(habit), mapOf(habitId to snaps), pauses, records, d, today
                    ).map { DayDetailItem(it.habit, it.record, it.state, it.targetValue) }
                } ?: emptyList()

                DetailUiState(
                    habit = habit,
                    stats = stats,
                    month = m,
                    selectedDate = sel,
                    selectedItems = items,
                    heatmap = heatmap,
                    trend = trend,
                    history = byDate.values.sortedByDescending { it.date }.take(60),
                    monthState = monthStates,
                    paused = pauses.any { it.habitId == habitId && it.endDate == null },
                    loading = false,
                    snack = snack.value,
                    monthRate = monthRate
                )
            }.flowOn(Dispatchers.Default).collect { _state.value = it }
        }
    }

    fun prevMonth() { month.update { it.minusMonths(1) } }
    fun nextMonth() { month.update { it.plusMonths(1) } }
    fun selectDate(d: LocalDate) { selected.value = if (selected.value == d) null else d }

    fun toggleToday() {
        val habit = _state.value.habit ?: return
        val today = LocalDate.now()
        viewModelScope.launch {
            val existing = repo.getRecord(habitId, today)
            if (existing?.status == RecordStatus.DONE) {
                repo.clearRecord(habitId, today)
                snack.value = "已撤销"
            } else {
                repo.setStatus(habitId, today, RecordStatus.DONE)
                snack.value = "已完成"
            }
        }
    }

    fun setStatusOn(date: LocalDate, status: RecordStatus) {
        if (date.isAfter(LocalDate.now())) return
        viewModelScope.launch {
            repo.setStatus(habitId, date, status)
            snack.value = when (status) {
                RecordStatus.DONE -> "已记录完成"
                RecordStatus.FAILED -> "已记录未达成"
                RecordStatus.SKIPPED -> "已跳过"
            }
        }
    }

    fun clearOn(date: LocalDate) {
        viewModelScope.launch {
            repo.clearRecord(habitId, date)
            snack.value = "已清除该日记录"
        }
    }

    fun setNote(date: LocalDate, note: String) {
        viewModelScope.launch { repo.setNote(habitId, date, note) }
    }

    fun addValue(date: LocalDate, delta: Double) {
        viewModelScope.launch { repo.addValue(habitId, date, delta) }
    }

    fun undoValue(date: LocalDate) {
        viewModelScope.launch { repo.undoLastValue(habitId, date) }
    }

    fun togglePause() {
        viewModelScope.launch {
            if (_state.value.paused) {
                repo.resumeHabit(habitId, LocalDate.now())
                snack.value = "已恢复，暂停期间不计入完成率"
            } else {
                repo.pauseHabit(habitId, LocalDate.now())
                snack.value = "已暂停，暂停期间不计入完成率"
            }
        }
    }

    fun archive() {
        viewModelScope.launch {
            repo.archiveHabit(habitId, true)
            snack.value = "已归档，历史记录保留"
        }
    }

    fun requestDelete() { _state.update { it.copy(showDeleteConfirm = true) } }
    fun cancelDelete() { _state.update { it.copy(showDeleteConfirm = false) } }

    fun confirmDelete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.deleteHabit(habitId)
            _state.update { it.copy(showDeleteConfirm = false) }
            onDeleted()
        }
    }

    fun consumeSnack() { snack.value = null }
}

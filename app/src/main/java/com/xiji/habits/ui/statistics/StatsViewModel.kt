package com.xiji.habits.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiji.habits.data.preferences.SettingsRepository
import com.xiji.habits.data.preferences.WeekStartSetting
import com.xiji.habits.data.repository.HabitRepository
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitPausePeriod
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.ScheduleSnapshot
import com.xiji.habits.domain.model.WeekStart
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

data class HabitStatRow(
    val habit: Habit,
    val stats: StatisticsEngine.HabitStats
)

data class StatsUiState(
    val overview: StatisticsEngine.Overview = StatisticsEngine.Overview(0, 0, 0, "天", 0f, 0f, emptyList()),
    val habitStats: List<HabitStatRow> = emptyList(),
    val selectedHabitId: Long? = null,
    val heatmap: List<StatisticsEngine.DayAggregate> = emptyList(),
    val heatmapYear: Int = LocalDate.now().year,
    val selectedDay: LocalDate? = null,
    val selectedDayDone: Int = 0,
    val selectedDayScheduled: Int = 0,
    val weekTrend: List<StatisticsEngine.DayAggregate> = emptyList(),
    val monthTrend: List<StatisticsEngine.DayAggregate> = emptyList(),
    val hasData: Boolean = false,
    val loading: Boolean = true
)

class StatsViewModel(
    private val repo: HabitRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    private val selectedHabit = MutableStateFlow<Long?>(null)
    private val year = MutableStateFlow(LocalDate.now().year)
    private val selectedDay = MutableStateFlow<LocalDate?>(null)

    init {
        viewModelScope.launch {
            combine(
                selectedHabit, year, selectedDay,
                repo.habits, repo.records, repo.snapshots, repo.pauses, settingsRepo.settings
            ) { values ->
                val selHabit = values[0] as Long?
                val yr = values[1] as Int
                val selDay = values[2] as LocalDate?
                val habits = values[3] as List<Habit>
                val records = values[4] as List<HabitRecord>
                val snapshots = values[5] as List<ScheduleSnapshot>
                val pauses = values[6] as List<HabitPausePeriod>
                val wsSetting = (values[7] as com.xiji.habits.data.preferences.UserSettings).weekStart
                val ws = if (wsSetting == WeekStartSetting.MONDAY) WeekStart.MONDAY else WeekStart.SUNDAY
                val today = LocalDate.now()
                val snaps = snapshots.groupBy { it.habitId }

                val filteredHabits = if (selHabit == null) habits else habits.filter { it.id == selHabit }
                val filteredSnaps = if (selHabit == null) snaps else snaps.filterKeys { it == selHabit }

                val recordsByHabit = records.groupBy { it.habitId }
                val pausesByHabit = pauses.groupBy { it.habitId }
                val rows = habits.map { h ->
                    HabitStatRow(h, StatisticsEngine.habitStats(h, snaps[h.id].orEmpty(), pausesByHabit[h.id].orEmpty(), recordsByHabit[h.id].orEmpty(), today, ws))
                }
                val overview = StatisticsEngine.overview(
                    habits, snaps, pauses, records, today, ws, precomputedStats = rows.map { it.stats }
                )

                val heatStart = LocalDate.of(yr, 1, 1)
                val heatEnd = if (yr == today.year) today else LocalDate.of(yr, 12, 31)
                val heat = if (heatStart.isAfter(heatEnd)) emptyList() else StatisticsEngine.heatmap(
                    filteredHabits, filteredSnaps, pauses, records, heatStart, heatEnd, today
                )

                val dayAgg = selDay?.let { d ->
                    StatisticsEngine.heatmap(
                        filteredHabits, filteredSnaps, pauses, records, d, d, today
                    ).firstOrNull()
                }

                StatsUiState(
                    overview = overview,
                    habitStats = rows.sortedByDescending { it.stats.strength },
                    selectedHabitId = selHabit,
                    heatmap = heat,
                    heatmapYear = yr,
                    selectedDay = selDay,
                    selectedDayDone = dayAgg?.done ?: 0,
                    selectedDayScheduled = dayAgg?.scheduled ?: 0,
                    weekTrend = StatisticsEngine.trend(
                        filteredHabits, filteredSnaps, pauses, records, today.minusDays(6), today, today
                    ),
                    monthTrend = StatisticsEngine.trend(
                        filteredHabits, filteredSnaps, pauses, records, today.minusDays(29), today, today
                    ),
                    hasData = habits.isNotEmpty(),
                    loading = false
                )
            }.flowOn(Dispatchers.Default).collect { _state.value = it }
        }
    }

    fun selectHabit(id: Long?) { selectedHabit.value = id }
    fun setYear(y: Int) { year.value = y }
    fun selectDay(d: LocalDate?) { selectedDay.value = if (selectedDay.value == d) null else d }
}

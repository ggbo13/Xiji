package com.xiji.habits.ui.home

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repo: HabitRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val filter = MutableStateFlow(HabitFilter.ALL)
    private val categoryFilter = MutableStateFlow<Long?>(null)
    private val doneCollapsed = MutableStateFlow(false)
    private val snack = MutableStateFlow<String?>(null)
    private var lastAction: (suspend () -> Unit)? = null

    init {
        viewModelScope.launch {
            combine(
                selectedDate,
                filter,
                categoryFilter,
                doneCollapsed,
                repo.habits,
                repo.records,
                repo.snapshots,
                repo.pauses,
                repo.categories,
                settingsRepo.settings
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                buildState(
                    date = values[0] as LocalDate,
                    filter = values[1] as HabitFilter,
                    catFilter = values[2] as Long?,
                    collapsed = values[3] as Boolean,
                    habits = values[4] as List<Habit>,
                    records = values[5] as List<HabitRecord>,
                    snapshots = values[6] as List<ScheduleSnapshot>,
                    pauses = values[7] as List<HabitPausePeriod>,
                    categories = values[8] as List<com.xiji.habits.domain.model.HabitCategory>,
                    weekStart = (values[9] as com.xiji.habits.data.preferences.UserSettings).weekStart
                )
            }.flowOn(Dispatchers.Default).collect { state ->
                _uiState.update {
                    state.copy(
                        showUndo = it.showUndo,
                        undoMessage = it.undoMessage,
                        snackMessage = snack.value
                    )
                }
            }
        }
    }

    private fun buildState(
        date: LocalDate,
        filter: HabitFilter,
        catFilter: Long?,
        collapsed: Boolean,
        habits: List<Habit>,
        records: List<HabitRecord>,
        snapshots: List<ScheduleSnapshot>,
        pauses: List<HabitPausePeriod>,
        categories: List<com.xiji.habits.domain.model.HabitCategory>,
        weekStart: WeekStartSetting
    ): HomeUiState {
        val today = LocalDate.now()
        val ws = if (weekStart == WeekStartSetting.MONDAY) WeekStart.MONDAY else WeekStart.SUNDAY
        val active = habits.filter { !it.isArchived }
        val archived = habits.filter { it.isArchived }
        val pool = if (filter == HabitFilter.ARCHIVED) archived else active
        val filteredByCat = if (catFilter == null) pool else pool.filter { it.categoryId == catFilter }

        // 一次性建立索引，避免在循环里反复 filter / 线性查找
        val recordMap = records.associateBy { it.habitId to it.date }
        val recordsByHabit = records.groupBy { it.habitId }
        val snapsByHabit = snapshots.groupBy { it.habitId }
        val categoryNames = categories.associate { it.id to it.name }

        val rows = filteredByCat.mapNotNull { h ->
            val rec = recordMap[h.id to date]
            val snaps = snapsByHabit[h.id].orEmpty()
            val state = ScheduleEngine.dayState(h, snaps, pauses, rec, date, today)
            if (state == DayState.NOT_SCHEDULED && filter != HabitFilter.ARCHIVED) return@mapNotNull null
            // 列表只需要连续与本周配额，用轻量版本避免逐日扫描全历史
            val stats = StatisticsEngine.listStats(
                h, snaps, pauses, recordsByHabit[h.id].orEmpty(), today, ws
            )
            HabitRowUi(
                habit = h,
                state = state,
                record = rec,
                streakCount = stats.currentStreak,
                streakUnit = stats.streakUnit,
                targetValue = ScheduleEngine.snapshotFor(snaps, h, date)?.targetValue ?: h.targetValue,
                weekDone = stats.weekDone,
                weekTarget = stats.weekTarget,
                categoryName = categoryNames[h.categoryId],
                isPaused = state == DayState.PAUSED
            )
        }.sortedBy { it.habit.sortOrder }

        val shown = when (filter) {
            HabitFilter.PENDING -> rows.filter { it.state != DayState.DONE }
            HabitFilter.DONE -> rows.filter { it.state == DayState.DONE }
            else -> rows
        }

        val countable = rows.filter { it.state != DayState.SKIPPED && it.state != DayState.PAUSED && it.state != DayState.FUTURE }
        val completed = countable.count { it.state == DayState.DONE }
        val total = countable.size
        val progress = if (total == 0) 0f else completed.toFloat() / total

        // 七日日期条
        val startOfWeek = date.minusDays(
            if (ws == WeekStart.MONDAY) (date.dayOfWeek.value - 1).toLong()
            else ((date.dayOfWeek.value % 7)).toLong()
        )
        val recordsByDate = records.groupBy { it.date }
        val weekDays = (0..6).map { i ->
            val d = startOfWeek.plusDays(i.toLong())
            val dayRecords = recordsByDate[d].orEmpty().associateBy { it.habitId }
            var scheduled = 0
            var done = 0
            for (h in active) {
                if (ScheduleEngine.isCountableDay(h, snapsByHabit[h.id].orEmpty(), pauses, d, today)) {
                    scheduled++
                    if (dayRecords[h.id]?.status == RecordStatus.DONE) done++
                }
            }
            DayBarItem(
                date = d,
                dayLabel = weekdayLabel(d.dayOfWeek),
                dayOfMonth = d.dayOfMonth,
                isToday = d == today,
                isSelected = d == date,
                done = done,
                scheduled = scheduled,
                isFuture = d.isAfter(today)
            )
        }

        return HomeUiState(
            today = today,
            selectedDate = date,
            greeting = greetingFor(LocalTime.now()),
            dateLine = dateLine(date),
            completed = completed,
            total = total,
            progress = progress,
            encouragement = encouragement(progress, total, completed),
            weekDays = weekDays,
            rows = shown,
            categories = categories,
            filter = filter,
            categoryFilter = catFilter,
            doneCollapsed = collapsed,
            loading = false
        )
    }

    fun selectDate(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        selectedDate.value = date
    }

    fun setFilter(f: HabitFilter) { filter.value = f }
    fun setCategory(id: Long?) { categoryFilter.value = id }
    fun toggleDoneCollapsed() { doneCollapsed.value = !doneCollapsed.value }

    /**
     * 快捷打卡：完成/撤销。
     * 选中今天 → 正常打卡；选中今天以前的日期 → 补签（会写入 isBackfilled 标记）。
     * 撤销会记录上一步以便 Snackbar 恢复。
     */
    fun toggle(habit: Habit) {
        val date = selectedDate.value
        val today = LocalDate.now()
        if (date.isAfter(today)) return
        val isBackfill = date.isBefore(today)
        viewModelScope.launch {
            val existing = repo.getRecord(habit.id, date)
            if (existing?.status == RecordStatus.DONE) {
                lastAction = { repo.setStatus(habit.id, date, RecordStatus.DONE, backfill = isBackfill) }
                repo.clearRecord(habit.id, date)
                snack.value = if (isBackfill) "已撤销「${habit.name}」的补签" else "已撤销「${habit.name}」"
            } else {
                lastAction = {
                    if (existing == null) repo.clearRecord(habit.id, date)
                    else repo.setStatus(habit.id, date, existing.status)
                }
                repo.setStatus(habit.id, date, RecordStatus.DONE, backfill = isBackfill)
                snack.value = if (isBackfill) "已补签「${habit.name}」" else "已完成「${habit.name}」"
            }
        }
    }

    fun markFailed(habit: Habit) {
        viewModelScope.launch {
            repo.setStatus(habit.id, selectedDate.value, RecordStatus.FAILED)
            snack.value = "已记录「${habit.name}」今日未达成"
        }
    }

    fun skip(habit: Habit) {
        viewModelScope.launch {
            repo.setStatus(habit.id, selectedDate.value, RecordStatus.SKIPPED)
            snack.value = "已跳过「${habit.name}」"
        }
    }

    fun addValue(habit: Habit, delta: Double) {
        viewModelScope.launch {
            repo.addValue(habit.id, selectedDate.value, delta)
        }
    }

    fun undoLast() {
        val action = lastAction ?: return
        viewModelScope.launch {
            action()
            lastAction = null
            snack.value = "已恢复"
        }
    }

    fun consumeSnack() { snack.value = null }

    fun deleteHabit(id: Long) {
        viewModelScope.launch {
            repo.deleteHabit(id)
            snack.value = "习惯已删除"
        }
    }

    fun archiveHabit(id: Long, archived: Boolean) {
        viewModelScope.launch {
            repo.archiveHabit(id, archived)
            snack.value = if (archived) "已归档，历史记录保留" else "已恢复"
        }
    }

    /** 拖拽排序松手后一次性写入完整顺序（一次事务，避免逐条更新的中间态闪烁） */
    fun reorderAll(ids: List<Long>) {
        viewModelScope.launch { repo.reorder(ids) }
    }

    fun reorder(fromId: Long, toId: Long) {
        viewModelScope.launch {
            val list = _uiState.value.rows.map { it.habit.id }.toMutableList()
            val from = list.indexOf(fromId)
            val to = list.indexOf(toId)
            if (from < 0 || to < 0 || from == to) return@launch
            list.add(to, list.removeAt(from))
            repo.reorder(list)
        }
    }

    companion object {
        fun greetingFor(time: LocalTime): String = when (time.hour) {
            in 5..10 -> "早上好，今天也要加油。"
            in 11..13 -> "中午好，记得给自己留点空隙。"
            in 14..17 -> "下午好，慢慢来就好。"
            in 18..22 -> "晚上好，今天辛苦了。"
            else -> "夜深了，早点休息。"
        }

        fun dateLine(date: LocalDate): String {
            val w = when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> "星期一"; DayOfWeek.TUESDAY -> "星期二"; DayOfWeek.WEDNESDAY -> "星期三"
                DayOfWeek.THURSDAY -> "星期四"; DayOfWeek.FRIDAY -> "星期五"; DayOfWeek.SATURDAY -> "星期六"
                DayOfWeek.SUNDAY -> "星期日"
            }
            return "${date.year}年${date.monthValue}月${date.dayOfMonth}日 · $w"
        }

        fun weekdayLabel(d: DayOfWeek): String = when (d) {
            DayOfWeek.MONDAY -> "一"; DayOfWeek.TUESDAY -> "二"; DayOfWeek.WEDNESDAY -> "三"
            DayOfWeek.THURSDAY -> "四"; DayOfWeek.FRIDAY -> "五"; DayOfWeek.SATURDAY -> "六"
            DayOfWeek.SUNDAY -> "日"
        }

        fun encouragement(progress: Float, total: Int, done: Int): String = when {
            total == 0 -> "今天还没有安排，先创建一个习惯吧。"
            done == total -> "今天的计划都完成了，好好休息。"
            progress >= 0.8f -> "只差一点点了。"
            progress >= 0.5f -> "已经完成大半，继续保持。"
            progress > 0f -> "开始了就好，慢慢来。"
            else -> "今天的第一件事，从这里开始。"
        }
    }
}

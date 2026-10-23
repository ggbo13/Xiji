package com.xiji.habits.ui.home

import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitCategory
import com.xiji.habits.domain.model.HabitRecord
import java.time.LocalDate

/** 今日列表中的一行 */
data class HabitRowUi(
    val habit: Habit,
    val state: DayState,
    val record: HabitRecord?,
    val streakCount: Int,
    val streakUnit: String,
    val targetValue: Double,
    val weekDone: Int,
    val weekTarget: Int,
    val categoryName: String?,
    val isPaused: Boolean
)

data class DayBarItem(
    val date: LocalDate,
    val dayLabel: String,
    val dayOfMonth: Int,
    val isToday: Boolean,
    val isSelected: Boolean,
    val done: Int,
    val scheduled: Int,
    val isFuture: Boolean
)

enum class HabitFilter(val label: String) { ALL("全部"), PENDING("待完成"), DONE("已完成"), ARCHIVED("已归档") }

data class HomeUiState(
    val today: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val greeting: String = "",
    val dateLine: String = "",
    val completed: Int = 0,
    val total: Int = 0,
    val progress: Float = 0f,
    val encouragement: String = "",
    val weekDays: List<DayBarItem> = emptyList(),
    val rows: List<HabitRowUi> = emptyList(),
    val categories: List<HabitCategory> = emptyList(),
    val filter: HabitFilter = HabitFilter.ALL,
    val categoryFilter: Long? = null,
    val doneCollapsed: Boolean = false,
    val loading: Boolean = true,
    val showUndo: Boolean = false,
    val undoMessage: String = "",
    val snackMessage: String? = null
)

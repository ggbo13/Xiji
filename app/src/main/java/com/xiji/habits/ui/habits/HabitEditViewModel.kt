package com.xiji.habits.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiji.habits.data.repository.HabitRepository
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitCategory
import com.xiji.habits.domain.model.HabitReminder
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.ScheduleSpec
import com.xiji.habits.domain.model.ScheduleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class ReminderDraft(
    val id: Long = 0,
    val hour: Int = 8,
    val minute: Int = 0,
    val weekdaysMask: Int = 0b1111111,
    val enabled: Boolean = true
)

data class EditUiState(
    val habitId: Long = 0,
    val isEditing: Boolean = false,
    val name: String = "",
    val description: String = "",
    val icon: String = "book",
    val color: Long = 0xFF416B5B,
    val categoryId: Long? = null,
    val type: HabitType = HabitType.COMPLETE,
    val targetValue: String = "1",
    val targetUnit: String = "次",
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val weekdaysMask: Int = 0b1111111,
    val timesPerWeek: Int = 3,
    val intervalDays: Int = 2,
    val startDate: LocalDate = LocalDate.now(),
    val reminders: List<ReminderDraft> = emptyList(),
    val confirmQuitDaily: Boolean = true,
    val categories: List<HabitCategory> = emptyList(),
    val advancedExpanded: Boolean = false,
    val nameError: String? = null,
    val targetError: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val loaded: Boolean = false
)

class HabitEditViewModel(
    private val repo: HabitRepository,
    private val habitId: Long
) : ViewModel() {

    private val _state = MutableStateFlow(EditUiState(habitId = habitId, isEditing = habitId > 0))
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cats = repo.allCategoriesOnce()
            _state.update { it.copy(categories = cats) }
            if (habitId > 0) {
                val h = repo.getHabit(habitId)
                if (h != null) {
                    val reminders = repo.remindersFor(habitId).map {
                        ReminderDraft(it.id, it.hour, it.minute, it.weekdaysMask, it.enabled)
                    }
                    _state.update {
                        it.copy(
                            name = h.name,
                            description = h.description,
                            icon = h.icon,
                            color = h.color,
                            categoryId = h.categoryId,
                            type = h.type,
                            targetValue = formatTarget(h.targetValue),
                            targetUnit = h.targetUnit.ifBlank { "次" },
                            scheduleType = h.schedule.type,
                            weekdaysMask = h.schedule.weekdaysMask,
                            timesPerWeek = h.schedule.timesPerWeek,
                            intervalDays = h.schedule.intervalDays,
                            startDate = h.startDate,
                            reminders = reminders,
                            confirmQuitDaily = h.confirmQuitDaily,
                            loaded = true
                        )
                    }
                } else {
                    _state.update { it.copy(loaded = true) }
                }
            } else {
                _state.update { it.copy(loaded = true) }
            }
        }
    }

    private fun formatTarget(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    fun setName(v: String) = _state.update { it.copy(name = v, nameError = null) }
    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setIcon(v: String) = _state.update { it.copy(icon = v) }
    fun setColor(v: Long) = _state.update { it.copy(color = v) }
    fun setCategory(v: Long?) = _state.update { it.copy(categoryId = v) }
    fun setType(v: HabitType) = _state.update {
        it.copy(
            type = v,
            targetUnit = if (v == HabitType.QUANTITY) it.targetUnit.ifBlank { "次" } else it.targetUnit
        )
    }
    fun setTargetValue(v: String) = _state.update { it.copy(targetValue = v, targetError = null) }
    fun setTargetUnit(v: String) = _state.update { it.copy(targetUnit = v.take(6)) }
    fun setScheduleType(v: ScheduleType) = _state.update { it.copy(scheduleType = v) }
    fun toggleWeekday(index: Int) = _state.update {
        val mask = it.weekdaysMask xor (1 shl index)
        it.copy(weekdaysMask = if (mask == 0) it.weekdaysMask else mask)
    }
    fun setTimesPerWeek(v: Int) = _state.update { it.copy(timesPerWeek = v.coerceIn(1, 7)) }
    fun setIntervalDays(v: Int) = _state.update { it.copy(intervalDays = v.coerceIn(1, 30)) }
    fun setStartDate(d: LocalDate) = _state.update { it.copy(startDate = d) }
    fun toggleAdvanced() = _state.update { it.copy(advancedExpanded = !it.advancedExpanded) }
    fun setConfirmQuitDaily(v: Boolean) = _state.update { it.copy(confirmQuitDaily = v) }

    fun addReminder() = _state.update {
        if (it.reminders.size >= 4) it
        else it.copy(reminders = it.reminders + ReminderDraft(hour = 8, minute = 0))
    }

    fun updateReminder(index: Int, hour: Int, minute: Int) = _state.update {
        val list = it.reminders.toMutableList()
        if (index in list.indices) list[index] = list[index].copy(hour = hour, minute = minute)
        it.copy(reminders = list)
    }

    fun toggleReminder(index: Int) = _state.update {
        val list = it.reminders.toMutableList()
        if (index in list.indices) list[index] = list[index].copy(enabled = !list[index].enabled)
        it.copy(reminders = list)
    }

    fun removeReminder(index: Int) = _state.update {
        val list = it.reminders.toMutableList()
        if (index in list.indices) list.removeAt(index)
        it.copy(reminders = list)
    }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = "请填写习惯名称") }
            return
        }
        val target = if (s.type == HabitType.QUANTITY) s.targetValue.toDoubleOrNull() else 1.0
        if (s.type == HabitType.QUANTITY && (target == null || target <= 0 || target > 1_000_000)) {
            _state.update { it.copy(targetError = "请输入 0 到 1000000 之间的目标值") }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val spec = ScheduleSpec(
                type = s.scheduleType,
                weekdaysMask = s.weekdaysMask,
                timesPerWeek = s.timesPerWeek,
                intervalDays = s.intervalDays,
                anchorDate = s.startDate
            )
            if (s.isEditing) {
                val existing = repo.getHabit(s.habitId) ?: return@launch
                repo.updateHabit(
                    existing.copy(
                        name = s.name.trim(),
                        description = s.description.trim(),
                        icon = s.icon,
                        color = s.color,
                        categoryId = s.categoryId,
                        type = s.type,
                        targetValue = target ?: 1.0,
                        targetUnit = if (s.type == HabitType.QUANTITY) s.targetUnit.trim() else "",
                        schedule = spec,
                        confirmQuitDaily = s.confirmQuitDaily
                    )
                )
                repo.setReminders(
                    s.habitId,
                    s.reminders.filter { it.enabled }.map {
                        HabitReminder(0, s.habitId, true, it.hour, it.minute, it.weekdaysMask)
                    }
                )
            } else {
                repo.createHabit(
                    Habit(
                        id = 0,
                        name = s.name.trim(),
                        description = s.description.trim(),
                        icon = s.icon,
                        color = s.color,
                        categoryId = s.categoryId,
                        type = s.type,
                        targetValue = target ?: 1.0,
                        targetUnit = if (s.type == HabitType.QUANTITY) s.targetUnit.trim() else "",
                        schedule = spec,
                        startDate = s.startDate,
                        sortOrder = 0,
                        archivedAt = null,
                        confirmQuitDaily = s.confirmQuitDaily,
                        createdAt = now,
                        updatedAt = now
                    ),
                    s.reminders.filter { it.enabled }.map {
                        HabitReminder(0, 0, true, it.hour, it.minute, it.weekdaysMask)
                    }
                )
            }
            _state.update { it.copy(saving = false, saved = true) }
        }
    }
}

fun LocalTime.formatHm(): String = "%02d:%02d".format(hour, minute)

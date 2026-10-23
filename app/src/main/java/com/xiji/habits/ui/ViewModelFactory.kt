package com.xiji.habits.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.xiji.habits.AppContainer
import com.xiji.habits.ui.backup.BackupViewModel
import com.xiji.habits.ui.calendar.CalendarViewModel
import com.xiji.habits.ui.habits.HabitDetailViewModel
import com.xiji.habits.ui.habits.HabitEditViewModel
import com.xiji.habits.ui.home.HomeViewModel
import com.xiji.habits.ui.settings.SettingsViewModel
import com.xiji.habits.ui.statistics.StatsViewModel

class XijiViewModelFactory(
    private val container: AppContainer,
    private val habitId: Long = 0
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(container.habitRepository, container.settingsRepository) as T
        modelClass.isAssignableFrom(CalendarViewModel::class.java) ->
            CalendarViewModel(container.habitRepository) as T
        modelClass.isAssignableFrom(StatsViewModel::class.java) ->
            StatsViewModel(container.habitRepository, container.settingsRepository) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(
                container.settingsRepository,
                container.habitRepository,
                container.database
            ) as T
        modelClass.isAssignableFrom(HabitEditViewModel::class.java) ->
            HabitEditViewModel(container.habitRepository, habitId) as T
        modelClass.isAssignableFrom(HabitDetailViewModel::class.java) ->
            HabitDetailViewModel(container.habitRepository, container.settingsRepository, habitId) as T
        modelClass.isAssignableFrom(BackupViewModel::class.java) ->
            BackupViewModel(container.backupManager) as T
        else -> error("Unknown ViewModel: ${modelClass.name}")
    }
}

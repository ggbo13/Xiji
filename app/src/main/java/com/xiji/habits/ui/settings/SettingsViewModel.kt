package com.xiji.habits.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiji.habits.data.local.AppDatabase
import com.xiji.habits.data.preferences.SettingsRepository
import com.xiji.habits.data.preferences.UserSettings
import com.xiji.habits.data.preferences.WeekStartSetting
import com.xiji.habits.data.repository.HabitRepository
import com.xiji.habits.domain.model.HabitCategory
import com.xiji.habits.ui.theme.AccentTheme
import com.xiji.habits.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val categories: List<HabitCategory> = emptyList(),
    val habitCount: Int = 0,
    val recordCount: Int = 0,
    val exactAlarmAllowed: Boolean = true,
    val message: String? = null,
    val showCategoryDialog: Boolean = false,
    val editingCategory: HabitCategory? = null,
    val showCategoryManager: Boolean = false,
    val categoryUsage: Map<Long, Int> = emptyMap(),
    val pendingDelete: HabitCategory? = null
)

class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val repo: HabitRepository,
    private val db: AppDatabase
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.categories.collect { list -> _state.update { it.copy(categories = list) } }
        }
        refreshCounts()
    }

    fun refreshCounts() {
        viewModelScope.launch {
            val habits = db.habitDao().count()
            val records = db.recordDao().getAllOnce().size
            _state.update { it.copy(habitCount = habits, recordCount = records) }
        }
    }

    fun setExactAlarmAllowed(v: Boolean) = _state.update { it.copy(exactAlarmAllowed = v) }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    fun setAccent(key: String) = viewModelScope.launch { settingsRepo.setAccent(key) }
    fun setDynamic(v: Boolean) = viewModelScope.launch { settingsRepo.setDynamicColor(v) }
    fun setWeekStart(v: WeekStartSetting) = viewModelScope.launch { settingsRepo.setWeekStart(v) }
    fun setReduceAnimation(v: Boolean) = viewModelScope.launch { settingsRepo.setReduceAnimation(v) }
    fun setLanguage(v: String) = viewModelScope.launch { settingsRepo.setLanguage(v) }

    fun openCategoryDialog(category: HabitCategory? = null) =
        _state.update { it.copy(showCategoryDialog = true, editingCategory = category) }

    fun dismissCategoryDialog() = _state.update { it.copy(showCategoryDialog = false, editingCategory = null) }

    /** 打开分类管理面板：列出全部分类及其使用数量 */
    fun openCategoryManager() {
        viewModelScope.launch {
            val usage = db.habitDao().getAllOnce()
                .mapNotNull { it.categoryId }
                .groupingBy { it }
                .eachCount()
            _state.update { it.copy(showCategoryManager = true, categoryUsage = usage) }
        }
    }

    fun closeCategoryManager() =
        _state.update { it.copy(showCategoryManager = false, pendingDelete = null) }

    /** 请求删除分类：先记录待删对象，由 UI 弹出确认 */
    fun requestDeleteCategory(category: HabitCategory) =
        _state.update { it.copy(pendingDelete = category) }

    fun cancelDeleteCategory() = _state.update { it.copy(pendingDelete = null) }

    /**
     * 确认删除分类。
     * 只解除习惯与该分类的关联，习惯本身与历史记录全部保留。
     */
    fun confirmDeleteCategory() {
        val target = _state.value.pendingDelete ?: return
        viewModelScope.launch {
            repo.deleteCategory(target.id)
            val usage = db.habitDao().getAllOnce()
                .mapNotNull { it.categoryId }
                .groupingBy { it }
                .eachCount()
            _state.update {
                it.copy(
                    pendingDelete = null,
                    categoryUsage = usage,
                    message = "已删除分类「${target.name}」，其中的习惯仍然保留"
                )
            }
        }
    }

    fun saveCategory(name: String, color: Long) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val editing = _state.value.editingCategory
            repo.saveCategory(
                HabitCategory(
                    id = editing?.id ?: 0,
                    name = name.trim(),
                    color = color,
                    sortOrder = editing?.sortOrder ?: _state.value.categories.size
                )
            )
            _state.update { it.copy(showCategoryDialog = false, editingCategory = null) }
        }
    }

    fun deleteCategory(id: Long) = viewModelScope.launch {
        repo.deleteCategory(id)
        _state.update { it.copy(message = "分类已删除，习惯仍会保留") }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

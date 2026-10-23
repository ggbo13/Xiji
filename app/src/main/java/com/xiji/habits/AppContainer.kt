package com.xiji.habits

import android.app.Application
import android.content.Context
import com.xiji.habits.backup.BackupManager
import com.xiji.habits.data.local.AppDatabase
import com.xiji.habits.data.preferences.SettingsRepository
import com.xiji.habits.data.repository.HabitRepository
import com.xiji.habits.reminder.ReminderScheduler
import com.xiji.habits.widget.WidgetRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * 极简手动依赖容器。本项目规模下引入 Hilt 收益有限，
 * 用显式构造保持依赖关系一目了然。
 */
class AppContainer(val context: Context) {
    val database: AppDatabase = AppDatabase.get(context)
    val habitRepository = HabitRepository(database) { notifyDataChanged() }
    val settingsRepository = SettingsRepository(context)
    val reminderScheduler = ReminderScheduler(context, database)
    val backupManager = BackupManager(context, database, habitRepository, settingsRepository)

    /**
     * 数据变更信号。任何会改变「今日应完成/已完成」的写操作都会触发一次，
     * 由 [observeDataChanges] 合并后刷新桌面小组件。
     *
     * 这是兜底机制：即使某处忘记显式调用 WidgetRefresh，组件也会在 400ms 内更新。
     */
    private val dataChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    fun notifyDataChanged() {
        dataChanges.tryEmit(Unit)
    }

    fun startWidgetSync() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            // debounce 合并连续操作（例如批量补签、拖拽排序）
            dataChanges.debounce(400).collect {
                WidgetRefresh.refreshNow(context)
            }
        }
    }
}

class XijiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.startWidgetSync()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as XijiApplication).container

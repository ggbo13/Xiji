package com.xiji.habits.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 小组件刷新的唯一入口。
 *
 * 之前只有「在小组件里勾选」这一条路径会刷新，因此在 App 内新建/编辑/删除习惯后，
 * 桌面组件要等系统 30 分钟周期更新才会变。现在所有写操作都会经过这里。
 *
 * 设计要点：
 * - 用一个应用级 scope，调用方不需要是 suspend（UI 回调里也能直接调）。
 * - 逐个组件 try/catch：某个组件未添加到桌面时 updateAll 会抛异常，
 *   不能让它影响另一个组件的刷新。
 * - 不用 WorkManager，避免引入额外后台任务与权限。
 */
object WidgetRefresh {

    private const val TAG = "WidgetRefresh"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            refreshNow(appContext)
        }
    }

    suspend fun refreshNow(context: Context) {
        runCatching { ProgressWidget().updateAll(context) }
            .onFailure { Log.w(TAG, "刷新 2×2 进度组件失败：${it.message}") }
        runCatching { TodayWidget().updateAll(context) }
            .onFailure { Log.w(TAG, "刷新 4×2 列表组件失败：${it.message}") }
    }
}

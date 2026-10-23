package com.xiji.habits.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.xiji.habits.MainActivity
import com.xiji.habits.domain.model.RecordStatus
import java.time.LocalDate

object WidgetKeys {
    val HABIT_ID = ActionParameters.Key<Long>("habit_id")
    val ACTION = ActionParameters.Key<String>("action")
}

/** 小组件里可执行的动作，与习惯类型一一对应 */
enum class WidgetAction(val key: String) {
    TOGGLE_COMPLETE("toggle"),
    ADD_ONE("add_one"),
    QUIT_HOLD("quit_hold"),
    QUIT_FAIL("quit_fail"),
    OPEN_HABIT("open");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/**
 * 小组件的统一动作处理器。
 *
 * 所有写操作都走 Repository（与 App 内完全同一条路径），
 * 因此统计、连续、补签标记等语义完全一致，不存在「组件里打卡不算数」的问题。
 */
class WidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val habitId = parameters[WidgetKeys.HABIT_ID] ?: return
        val action = WidgetAction.of(parameters[WidgetKeys.ACTION]) ?: return
        val app = context.applicationContext as com.xiji.habits.XijiApplication
        val repo = app.container.habitRepository
        val today = LocalDate.now()

        when (action) {
            WidgetAction.OPEN_HABIT -> {
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("habit_id", habitId)
                    }
                )
                return
            }
            WidgetAction.TOGGLE_COMPLETE -> {
                val existing = repo.getRecord(habitId, today)
                if (existing?.status == RecordStatus.DONE) repo.clearRecord(habitId, today)
                else repo.setStatus(habitId, today, RecordStatus.DONE)
            }
            WidgetAction.ADD_ONE -> {
                // 数量型：累加 1 个单位；达到目标后 Repository 会自动置为 DONE
                repo.addValue(habitId, today, 1.0)
            }
            WidgetAction.QUIT_HOLD -> {
                repo.setStatus(habitId, today, RecordStatus.DONE)
            }
            WidgetAction.QUIT_FAIL -> {
                val existing = repo.getRecord(habitId, today)
                if (existing?.status == RecordStatus.FAILED) repo.clearRecord(habitId, today)
                else repo.setStatus(habitId, today, RecordStatus.FAILED)
            }
        }
        // Repository 已发出变更信号；这里同步刷新一次让点击反馈立即可见
        WidgetRefresh.refreshNow(context)
    }
}

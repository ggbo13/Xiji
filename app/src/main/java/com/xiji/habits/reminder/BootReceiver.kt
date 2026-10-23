package com.xiji.habits.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiji.habits.XijiApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 设备重启、时间/时区变化后恢复提醒。
 * 这些广播均为系统广播，不需要 INTERNET 权限。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                ReminderScheduler.ACTION_RESCHEDULE
            )
        ) return

        val app = context.applicationContext as? XijiApplication ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.reminderScheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}

package com.xiji.habits.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xiji.habits.MainActivity
import com.xiji.habits.R
import com.xiji.habits.XijiApplication
import com.xiji.habits.data.local.entity.RecordEntity
import com.xiji.habits.data.repository.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(ReminderScheduler.EXTRA_HABIT_ID, -1)
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1)
        val weekday = intent.getIntExtra(ReminderScheduler.EXTRA_WEEKDAY, -1)
        if (habitId <= 0) return

        val app = context.applicationContext as? XijiApplication ?: return
        val container = app.container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = container.database
                val habit = db.habitDao().getById(habitId)
                val today = LocalDate.now()
                if (habit != null && habit.archivedAt == null) {
                    // 已完成则不再打扰
                    val record = db.recordDao().find(habitId, today.toString())
                    val done = record?.status == "DONE"
                    // 暂停中不提醒
                    val paused = db.pauseDao().getByHabit(habitId).any { it.endDate == null }
                    if (!done && !paused) {
                        showNotification(context, habitId, habit.name, habit.icon, habit.habitType)
                    }
                }
                // 重新安排下一周的同一天
                if (reminderId > 0 && weekday >= 0) {
                    val reminders = db.reminderDao().getByHabit(habitId)
                    reminders.firstOrNull { it.id == reminderId }?.let {
                        container.reminderScheduler.schedule(it)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        habitId: Long,
        name: String,
        icon: String,
        habitType: String
    ) {
        createChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("habit_id", habitId)
        }
        val contentIntent = PendingIntent.getActivity(
            context, habitId.toInt(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = when (habitType) {
            "QUIT" -> "今天也记得确认一下，守住它。"
            "QUANTITY" -> "该记录今天的进度了。"
            else -> "到时间了，完成今天的这一次。"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            try {
                NotificationManagerCompat.from(context).notify(habitId.toInt(), notification)
            } catch (_: SecurityException) {
                // 未授予通知权限时静默失败，核心功能不受影响
            }
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "习惯提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "本地习惯提醒，不联网"
                        enableVibration(true)
                    }
                )
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "habit_reminders"
    }
}

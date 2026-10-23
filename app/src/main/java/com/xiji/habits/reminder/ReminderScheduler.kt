package com.xiji.habits.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.xiji.habits.data.local.AppDatabase
import com.xiji.habits.data.local.entity.ReminderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 提醒调度。
 *
 * 精度要求是「到分钟」，因此使用 AlarmManager.setExactAndAllowWhileIdle，
 * 而不是 WorkManager 的周期任务（后者最小间隔 15 分钟且不保证准时）。
 * 每个提醒使用稳定的 requestCode（reminderId * 8 + weekday），便于精确取消。
 */
class ReminderScheduler(
    private val context: Context,
    private val db: AppDatabase
) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager.canScheduleExactAlarms()
    }

    /** 重新安排全部提醒（启动、重启、时间/时区变化后调用） */
    suspend fun rescheduleAll() = withContext(Dispatchers.IO) {
        val reminders = db.reminderDao().getAllOnce().filter { it.enabled }
        reminders.forEach { schedule(it) }
    }

    suspend fun schedule(reminder: ReminderEntity) = withContext(Dispatchers.IO) {
        cancel(reminder)
        if (!reminder.enabled) return@withContext
        for (weekday in 0..6) {
            if ((reminder.weekdaysMask shr weekday) and 1 == 0) continue
            val trigger = nextTrigger(reminder.hour, reminder.minute, weekday)
            val pi = pendingIntent(reminder, weekday)
            try {
                if (canScheduleExact()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                } else {
                    // 没有精确闹钟权限时退化为不精确闹钟，仍然可用
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                }
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        }
    }

    fun cancel(reminder: ReminderEntity) {
        for (weekday in 0..6) {
            alarmManager.cancel(pendingIntent(reminder, weekday))
        }
    }

    fun cancelForHabit(habitId: Long) {
        // requestCode 编码了 reminderId，无法反查 habitId 时由调用方先删除再统一重排
        context.sendBroadcast(Intent(ACTION_RESCHEDULE).setPackage(context.packageName))
    }

    private fun pendingIntent(reminder: ReminderEntity, weekday: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_HABIT_ID, reminder.habitId)
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_WEEKDAY, weekday)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(reminder.id, weekday),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(reminderId: Long, weekday: Int): Int =
        ((reminderId % 200_000) * 8 + weekday).toInt()

    companion object {
        const val ACTION_RESCHEDULE = "com.xiji.habits.ACTION_RESCHEDULE"
        const val EXTRA_HABIT_ID = "habit_id"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_WEEKDAY = "weekday"

        /** 下一次触发时间：严格晚于当前时间 */
        fun nextTrigger(hour: Int, minute: Int, weekday: Int, now: LocalDateTime = LocalDateTime.now()): Long {
            var date: LocalDate = now.toLocalDate()
            // java DayOfWeek: MONDAY=1 ... SUNDAY=7；我们的 index0: 0=周一
            val targetDow = weekday + 1
            while (date.dayOfWeek.value != targetDow) {
                date = date.plusDays(1)
            }
            var candidate = LocalDateTime.of(date, LocalTime.of(hour, minute))
            if (!candidate.isAfter(now)) {
                candidate = candidate.plusWeeks(1)
            }
            return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }
}

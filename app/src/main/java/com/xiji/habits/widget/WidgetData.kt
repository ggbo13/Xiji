package com.xiji.habits.widget

import android.content.Context
import com.xiji.habits.data.toDomain
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.formatValue
import com.xiji.habits.domain.schedule.ScheduleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 小组件里的一行。三种习惯类型共用同一个结构，用 [type] 区分可执行的操作：
 * - COMPLETE：勾选 / 取消完成
 * - QUANTITY：+1（达到目标自动完成），长按或点名称进入 App 精确修改
 * - QUIT：标记「守住了」/「破戒」
 */
data class WidgetHabit(
    val id: Long,
    val name: String,
    val icon: String,
    val color: Long,
    val type: HabitType,
    val state: DayState,
    val value: Double,
    val target: Double,
    val unit: String
) {
    val done: Boolean get() = state == DayState.DONE
    val failed: Boolean get() = state == DayState.FAILED

    /** 数量型进度文案，例如 1200/2000mL */
    val quantityLabel: String?
        get() = if (type == HabitType.QUANTITY) {
            "${formatValue(value)}/${formatValue(target)}${unit}"
        } else null

    /** 右侧操作按钮文案；null 表示该行当前不可操作 */
    val actionLabel: String?
        get() = when (type) {
            HabitType.COMPLETE -> if (done) null else "完成"
            HabitType.QUANTITY -> if (done) null else "+1"
            HabitType.QUIT -> when {
                done -> null
                failed -> "重来"
                else -> "守住"
            }
        }
}

data class WidgetSnapshot(
    val date: LocalDate,
    val completed: Int,
    val total: Int,
    val habits: List<WidgetHabit>
) {
    val progress: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

/**
 * 小组件读取数据的唯一入口。直接查 Room，保证与 App 内状态一致。
 *
 * 只查「今天」需要的记录（按日期过滤），而不是把全部历史读进内存——
 * 组件是高频刷新的入口，不能因为它把整张表拉起来。
 */
object WidgetData {

    suspend fun load(context: Context, maxItems: Int = 20): WidgetSnapshot = withContext(Dispatchers.IO) {
        val container = (context.applicationContext as com.xiji.habits.XijiApplication).container
        val db = container.database
        val today = LocalDate.now()
        val iso = today.toString()

        val habits = db.habitDao().getAllOnce().filter { it.archivedAt == null }.map { it.toDomain() }
        val todayRecords = db.recordDao().getByDateOnce(iso).map { it.toDomain() }
        val snapshots = db.snapshotDao().getAllOnce().map { it.toDomain() }
        val pauses = db.pauseDao().getAllOnce().map { it.toDomain() }

        val recordByHabit = todayRecords.associateBy { it.habitId }
        val snapsByHabit = snapshots.groupBy { it.habitId }

        val rows = habits.mapNotNull { h ->
            val snaps = snapsByHabit[h.id].orEmpty()
            val rec = recordByHabit[h.id]
            val state = ScheduleEngine.dayState(h, snaps, pauses, rec, today, today)
            if (state == DayState.NOT_SCHEDULED || state == DayState.PAUSED) return@mapNotNull null
            WidgetHabit(
                id = h.id,
                name = h.name,
                icon = h.icon,
                color = h.color,
                type = h.type,
                state = state,
                value = rec?.accumulatedValue ?: 0.0,
                target = ScheduleEngine.snapshotFor(snaps, h, today)?.targetValue ?: h.targetValue,
                unit = h.targetUnit
            )
        }.sortedWith(compareBy({ it.done }, { it.state == DayState.FAILED }, { it.name }))

        WidgetSnapshot(
            date = today,
            completed = rows.count { it.done },
            total = rows.size,
            habits = rows.take(maxItems)
        )
    }
}

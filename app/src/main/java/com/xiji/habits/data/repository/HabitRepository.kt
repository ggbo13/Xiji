package com.xiji.habits.data.repository

import androidx.room.withTransaction
import com.xiji.habits.data.local.AppDatabase
import com.xiji.habits.data.toDomain
import com.xiji.habits.data.toEntity
import com.xiji.habits.data.toIso
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitCategory
import com.xiji.habits.domain.model.HabitPausePeriod
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.HabitReminder
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 唯一的持久化事实来源。所有页面通过它读取数据，所有写操作都在这里落地。
 */
class HabitRepository(
    private val db: AppDatabase,
    /** 数据变更回调：用于刷新桌面小组件。默认空实现，便于单元测试。 */
    private val onDataChanged: () -> Unit = {}
) {

    private val habitDao = db.habitDao()
    private val recordDao = db.recordDao()
    private val categoryDao = db.categoryDao()
    private val reminderDao = db.reminderDao()
    private val pauseDao = db.pauseDao()
    private val snapshotDao = db.snapshotDao()
    private val valueEventDao = db.valueEventDao()

    val habits: Flow<List<Habit>> = habitDao.observeActive().map { list -> list.map { it.toDomain() } }
    val allHabits: Flow<List<Habit>> = habitDao.observeAll().map { list -> list.map { it.toDomain() } }
    val records: Flow<List<HabitRecord>> = recordDao.observeAll().map { list -> list.map { it.toDomain() } }
    val categories: Flow<List<HabitCategory>> = categoryDao.observeAll().map { list -> list.map { it.toDomain() } }
    val reminders: Flow<List<HabitReminder>> = reminderDao.observeAll().map { list -> list.map { it.toDomain() } }
    val pauses: Flow<List<HabitPausePeriod>> = pauseDao.observeAll().map { list -> list.map { it.toDomain() } }
    val snapshots: Flow<List<ScheduleSnapshot>> = snapshotDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeHabit(id: Long): Flow<Habit?> = habitDao.observeById(id).map { it?.toDomain() }

    fun observeRecords(habitId: Long): Flow<List<HabitRecord>> =
        recordDao.observeByHabit(habitId).map { list -> list.map { it.toDomain() } }

    fun observeRecordsBetween(from: LocalDate, to: LocalDate): Flow<List<HabitRecord>> =
        recordDao.observeBetween(from.toIso(), to.toIso()).map { list -> list.map { it.toDomain() } }

    fun observeRecord(habitId: Long, date: LocalDate): Flow<HabitRecord?> =
        recordDao.observeFind(habitId, date.toIso()).map { it?.toDomain() }

    fun observeReminders(habitId: Long): Flow<List<HabitReminder>> =
        reminderDao.observeByHabit(habitId).map { list -> list.map { it.toDomain() } }

    suspend fun getHabit(id: Long): Habit? = habitDao.getById(id)?.toDomain()

    suspend fun allHabitsOnce(): List<Habit> = habitDao.getAllOnce().map { it.toDomain() }
    suspend fun allRecordsOnce(): List<HabitRecord> = recordDao.getAllOnce().map { it.toDomain() }
    suspend fun allSnapshotsOnce(): List<ScheduleSnapshot> = snapshotDao.getAllOnce().map { it.toDomain() }
    suspend fun allPausesOnce(): List<HabitPausePeriod> = pauseDao.getAllOnce().map { it.toDomain() }
    suspend fun allCategoriesOnce(): List<HabitCategory> = categoryDao.getAllOnce().map { it.toDomain() }
    suspend fun allRemindersOnce(): List<HabitReminder> = reminderDao.getAllOnce().map { it.toDomain() }

    suspend fun remindersFor(habitId: Long): List<HabitReminder> =
        reminderDao.getByHabit(habitId).map { it.toDomain() }

    // ---------- 习惯 CRUD ----------

    suspend fun createHabit(habit: Habit, reminders: List<HabitReminder> = emptyList()): Long {
        val now = System.currentTimeMillis()
        val order = habitDao.nextSortOrder()
        val id = habitDao.insert(habit.copy(sortOrder = order, createdAt = now, updatedAt = now).toEntity())
        // 初始计划快照，保证历史统计有据可依
        snapshotDao.insert(
            ScheduleSnapshot(
                id = 0,
                habitId = id,
                effectiveFrom = habit.startDate,
                schedule = habit.schedule,
                targetValue = habit.targetValue,
                targetUnit = habit.targetUnit,
                createdAt = now
            ).toEntity()
        )
        reminders.forEach { r ->
            reminderDao.insert(
                com.xiji.habits.data.local.entity.ReminderEntity(
                    habitId = id,
                    enabled = r.enabled,
                    hour = r.hour,
                    minute = r.minute,
                    weekdaysMask = r.weekdaysMask
                )
            )
        }
        onDataChanged()
        return id
    }

    /**
     * 更新习惯。若周期/目标发生变化，则写入新的计划快照（从今天生效），
     * 历史日期继续沿用旧快照，避免重算历史完成率。
     */
    suspend fun updateHabit(habit: Habit, effectiveFrom: LocalDate = LocalDate.now()) {
        val existing = habitDao.getById(habit.id) ?: return
        val now = System.currentTimeMillis()
        val scheduleChanged = existing.scheduleType != habit.schedule.type.name ||
            existing.selectedWeekdays != habit.schedule.weekdaysMask ||
            existing.timesPerWeek != habit.schedule.timesPerWeek ||
            existing.intervalDays != habit.schedule.intervalDays ||
            existing.targetValue != habit.targetValue ||
            existing.targetUnit != habit.targetUnit

        habitDao.update(habit.copy(updatedAt = now).toEntity())

        if (scheduleChanged) {
            val last = snapshotDao.getByHabit(habit.id).lastOrNull()
            val same = last != null &&
                last.scheduleType == habit.schedule.type.name &&
                last.selectedWeekdays == habit.schedule.weekdaysMask &&
                last.timesPerWeek == habit.schedule.timesPerWeek &&
                last.intervalDays == habit.schedule.intervalDays &&
                last.targetValue == habit.targetValue &&
                last.targetUnit == habit.targetUnit
            if (!same) {
                snapshotDao.insert(
                    ScheduleSnapshot(
                        id = 0,
                        habitId = habit.id,
                        effectiveFrom = effectiveFrom,
                        schedule = habit.schedule,
                        targetValue = habit.targetValue,
                        targetUnit = habit.targetUnit,
                        createdAt = now
                    ).toEntity()
                )
            }
        }
        onDataChanged()
    }

    suspend fun setReminders(habitId: Long, reminders: List<HabitReminder>) {
        reminderDao.deleteByHabit(habitId)
        reminders.forEach {
            reminderDao.insert(
                com.xiji.habits.data.local.entity.ReminderEntity(
                    habitId = habitId,
                    enabled = it.enabled,
                    hour = it.hour,
                    minute = it.minute,
                    weekdaysMask = it.weekdaysMask
                )
            )
        }
    }

    suspend fun archiveHabit(id: Long, archived: Boolean) {
        habitDao.archive(id, if (archived) System.currentTimeMillis() else null)
        onDataChanged()
    }

    suspend fun deleteHabit(id: Long) {
        habitDao.deleteById(id)
        onDataChanged()
    }

    suspend fun reorder(ids: List<Long>) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            // 筛选后的列表只替换自己的槽位，未显示的习惯保持原位置。
            val all = habitDao.getAllOnce()
            val existing = all.map { it.id }.toSet()
            val requested = ids.distinct().filter { it in existing }
            val moved = requested.toSet()
            val order = requested.iterator()
            val merged = all.map { if (it.id in moved) order.next() else it.id }
            merged.forEachIndexed { index, id -> habitDao.updateSortOrder(id, index, now) }
        }
        onDataChanged()
    }

    // ---------- 打卡 ----------

    suspend fun getRecord(habitId: Long, date: LocalDate): HabitRecord? =
        recordDao.find(habitId, date.toIso())?.toDomain()

    /**
     * 写入某天的打卡状态。
     *
     * @param backfill true 表示这是事后补记（补签）。补签只记录事实，
     *   不提供任何额外奖励，也不会改变连续与完成率的计算口径。
     *   只有「今天以前的日期」才允许标记为补签。
     */
    suspend fun setStatus(
        habitId: Long,
        date: LocalDate,
        status: RecordStatus,
        note: String? = null,
        backfill: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val existing = recordDao.find(habitId, date.toIso())
        val habit = habitDao.getById(habitId) ?: return
        val target = habit.targetValue
        val value = when (status) {
            RecordStatus.DONE -> if (habit.habitType == "QUANTITY") maxOf(existing?.accumulatedValue ?: 0.0, target) else 1.0
            else -> existing?.accumulatedValue ?: 0.0
        }
        val isBackfill = backfill && date.isBefore(Clock.today())
        recordDao.upsert(
            com.xiji.habits.data.local.entity.RecordEntity(
                id = existing?.id ?: 0,
                habitId = habitId,
                localDate = date.toIso(),
                status = status.name,
                accumulatedValue = value,
                note = note ?: existing?.note,
                isBackfilled = isBackfill || (existing?.isBackfilled ?: false),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
        onDataChanged()
    }

    suspend fun clearRecord(habitId: Long, date: LocalDate) {
        recordDao.delete(habitId, date.toIso())
        valueEventDao.deleteForDay(habitId, date.toIso())
        onDataChanged()
    }

    /**
     * 数量型追加。写入事件表后重算当日累计，达标自动标记 DONE。
     * 同一天多次追加不会产生重复的完成记录。
     */
    suspend fun addValue(habitId: Long, date: LocalDate, delta: Double): HabitRecord? {
        if (delta == 0.0) return getRecord(habitId, date)
        val backfill = date.isBefore(Clock.today())
        val habit = habitDao.getById(habitId) ?: return null
        val now = System.currentTimeMillis()
        valueEventDao.insert(
            com.xiji.habits.data.local.entity.ValueEventEntity(
                habitId = habitId,
                localDate = date.toIso(),
                delta = delta,
                createdAt = now
            )
        )
        val events = valueEventDao.getForDay(habitId, date.toIso())
        val total = events.sumOf { it.delta }.coerceIn(0.0, MAX_VALUE_INPUT)
        val reached = total >= habit.targetValue && habit.targetValue > 0
        val existing = recordDao.find(habitId, date.toIso())
        recordDao.upsert(
            com.xiji.habits.data.local.entity.RecordEntity(
                id = existing?.id ?: 0,
                habitId = habitId,
                localDate = date.toIso(),
                status = if (reached) RecordStatus.DONE.name else RecordStatus.FAILED.name,
                accumulatedValue = total,
                note = existing?.note,
                isBackfilled = backfill || (existing?.isBackfilled ?: false),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
        onDataChanged()
        return getRecord(habitId, date)
    }

    /** 精确撤销最后一次追加 */
    suspend fun undoLastValue(habitId: Long, date: LocalDate): HabitRecord? {
        val events = valueEventDao.getForDay(habitId, date.toIso())
        val last = events.lastOrNull() ?: return getRecord(habitId, date)
        valueEventDao.deleteForDay(habitId, date.toIso())
        val now = System.currentTimeMillis()
        val remaining = events.dropLast(1)
        remaining.forEach {
            valueEventDao.insert(
                com.xiji.habits.data.local.entity.ValueEventEntity(
                    habitId = habitId,
                    localDate = date.toIso(),
                    delta = it.delta,
                    createdAt = it.createdAt
                )
            )
        }
        val total = remaining.sumOf { it.delta }.coerceAtLeast(0.0)
        val habit = habitDao.getById(habitId) ?: return null
        val existing = recordDao.find(habitId, date.toIso())
        if (total <= 0.0) {
            recordDao.delete(habitId, date.toIso())
            onDataChanged()
            return null
        }
        val reached = total >= habit.targetValue
        recordDao.upsert(
            com.xiji.habits.data.local.entity.RecordEntity(
                id = existing?.id ?: 0,
                habitId = habitId,
                localDate = date.toIso(),
                status = if (reached) RecordStatus.DONE.name else RecordStatus.FAILED.name,
                accumulatedValue = total,
                note = existing?.note,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
        return getRecord(habitId, date)
    }

    suspend fun setNote(habitId: Long, date: LocalDate, note: String?) {
        val existing = recordDao.find(habitId, date.toIso()) ?: return
        recordDao.upsert(existing.copy(note = note?.takeIf { it.isNotBlank() }, updatedAt = System.currentTimeMillis()))
    }

    // ---------- 暂停 ----------

    suspend fun pauseHabit(habitId: Long, from: LocalDate) {
        val open = pauseDao.getByHabit(habitId).firstOrNull { it.endDate == null }
        if (open != null) return
        pauseDao.insert(
            com.xiji.habits.data.local.entity.PauseEntity(
                habitId = habitId,
                startDate = from.toEpochDay(),
                endDate = null,
                reason = null
            )
        )
        onDataChanged()
    }

    suspend fun resumeHabit(habitId: Long, at: LocalDate) {
        val open = pauseDao.getByHabit(habitId).firstOrNull { it.endDate == null } ?: return
        pauseDao.close(open.id, at.minusDays(1).toEpochDay())
        onDataChanged()
    }

    // ---------- 分类 ----------

    suspend fun saveCategory(category: HabitCategory): Long =
        categoryDao.upsert(
            com.xiji.habits.data.local.entity.CategoryEntity(
                id = category.id, name = category.name, color = category.color, sortOrder = category.sortOrder
            )
        )

    suspend fun deleteCategory(id: Long) {
        categoryDao.detachHabits(id)
        categoryDao.delete(id)
        onDataChanged()
    }

    // ---------- 导入导出支持 ----------

    suspend fun insertRaw(
        habit: com.xiji.habits.data.local.entity.HabitEntity,
        records: List<com.xiji.habits.data.local.entity.RecordEntity>,
        reminders: List<com.xiji.habits.data.local.entity.ReminderEntity>,
        pauses: List<com.xiji.habits.data.local.entity.PauseEntity>,
        snapshots: List<com.xiji.habits.data.local.entity.ScheduleSnapshotEntity>,
        events: List<com.xiji.habits.data.local.entity.ValueEventEntity>
    ) {
        habitDao.insert(habit)
        records.forEach { recordDao.upsert(it) }
        reminders.forEach { reminderDao.insert(it) }
        pauses.forEach { pauseDao.insert(it) }
        snapshots.forEach { snapshotDao.insert(it) }
        events.forEach { valueEventDao.insert(it) }
        onDataChanged()
    }

    suspend fun clearAll() {
        db.clearAllTables()
        onDataChanged()
    }

    /** 备份导入完成后通知外部（小组件刷新等） */
    fun notifyImported() {
        onDataChanged()
    }

    companion object {
        const val MAX_VALUE_INPUT = 1_000_000.0
    }
}

/** 本地时间工具：统一「今天」的取值，便于测试注入 */
object Clock {
    var todayProvider: () -> LocalDate = { LocalDate.now() }
    fun today(): LocalDate = todayProvider()

    fun nowMillis(): Long = System.currentTimeMillis()

    fun toLocalDateTime(date: LocalDate, time: LocalTime): LocalDateTime = LocalDateTime.of(date, time)
    fun zone(): ZoneId = ZoneId.systemDefault()
}

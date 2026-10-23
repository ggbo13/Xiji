package com.xiji.habits.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.xiji.habits.data.local.entity.CategoryEntity
import com.xiji.habits.data.local.entity.HabitEntity
import com.xiji.habits.data.local.entity.PauseEntity
import com.xiji.habits.data.local.entity.RecordEntity
import com.xiji.habits.data.local.entity.ReminderEntity
import com.xiji.habits.data.local.entity.ScheduleSnapshotEntity
import com.xiji.habits.data.local.entity.ValueEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archivedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllOnce(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): HabitEntity?

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeById(id: Long): Flow<HabitEntity?>

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE habits SET sortOrder = :order, updatedAt = :now WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int, now: Long)

    @Query("UPDATE habits SET archivedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun archive(id: Long, at: Long?)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM habits")
    suspend fun nextSortOrder(): Int

    @Query("SELECT COUNT(*) FROM habits")
    suspend fun count(): Int
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE habitId = :habitId ORDER BY localDate DESC")
    fun observeByHabit(habitId: Long): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE localDate = :date ORDER BY habitId ASC")
    fun observeByDate(date: String): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE localDate BETWEEN :from AND :to ORDER BY localDate ASC")
    fun observeBetween(from: String, to: String): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records ORDER BY localDate ASC")
    fun observeAll(): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records ORDER BY localDate ASC")
    suspend fun getAllOnce(): List<RecordEntity>

    @Query("SELECT * FROM records WHERE habitId = :habitId AND localDate = :date LIMIT 1")
    suspend fun find(habitId: Long, date: String): RecordEntity?

    @Query("SELECT * FROM records WHERE localDate = :date")
    suspend fun getByDateOnce(date: String): List<RecordEntity>

    @Query("SELECT * FROM records WHERE habitId = :habitId AND localDate = :date LIMIT 1")
    fun observeFind(habitId: Long, date: String): Flow<RecordEntity?>

    @Upsert
    suspend fun upsert(record: RecordEntity): Long

    @Query("DELETE FROM records WHERE habitId = :habitId AND localDate = :date")
    suspend fun delete(habitId: Long, date: String)

    @Query("DELETE FROM records WHERE habitId = :habitId")
    suspend fun deleteByHabit(habitId: Long)

    @Query("SELECT COUNT(*) FROM records WHERE habitId = :habitId")
    suspend fun countByHabit(habitId: Long): Int
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY hour ASC, minute ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY hour ASC, minute ASC")
    suspend fun getAllOnce(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE habitId = :habitId")
    fun observeByHabit(habitId: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE habitId = :habitId")
    suspend fun getByHabit(habitId: Long): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("DELETE FROM reminders WHERE habitId = :habitId")
    suspend fun deleteByHabit(habitId: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllOnce(): List<CategoryEntity>

    @Upsert
    suspend fun upsert(category: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE habits SET categoryId = NULL WHERE categoryId = :id")
    suspend fun detachHabits(id: Long)
}

@Dao
interface PauseDao {
    @Query("SELECT * FROM pause_periods ORDER BY startDate DESC")
    fun observeAll(): Flow<List<PauseEntity>>

    @Query("SELECT * FROM pause_periods ORDER BY startDate DESC")
    suspend fun getAllOnce(): List<PauseEntity>

    @Query("SELECT * FROM pause_periods WHERE habitId = :habitId ORDER BY startDate DESC")
    suspend fun getByHabit(habitId: Long): List<PauseEntity>

    @Insert
    suspend fun insert(pause: PauseEntity): Long

    @Query("UPDATE pause_periods SET endDate = :end WHERE id = :id")
    suspend fun close(id: Long, end: Long)

    @Query("DELETE FROM pause_periods WHERE habitId = :habitId")
    suspend fun deleteByHabit(habitId: Long)
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM schedule_history ORDER BY effectiveFrom ASC")
    fun observeAll(): Flow<List<ScheduleSnapshotEntity>>

    @Query("SELECT * FROM schedule_history ORDER BY effectiveFrom ASC")
    suspend fun getAllOnce(): List<ScheduleSnapshotEntity>

    @Query("SELECT * FROM schedule_history WHERE habitId = :habitId ORDER BY effectiveFrom ASC")
    suspend fun getByHabit(habitId: Long): List<ScheduleSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: ScheduleSnapshotEntity): Long

    @Query("DELETE FROM schedule_history WHERE habitId = :habitId")
    suspend fun deleteByHabit(habitId: Long)
}

@Dao
interface ValueEventDao {
    @Query("SELECT * FROM value_events WHERE habitId = :habitId AND localDate = :date ORDER BY createdAt ASC")
    suspend fun getForDay(habitId: Long, date: String): List<ValueEventEntity>

    @Insert
    suspend fun insert(event: ValueEventEntity): Long

    @Query("DELETE FROM value_events WHERE habitId = :habitId AND localDate = :date")
    suspend fun deleteForDay(habitId: Long, date: String)

    @Query("SELECT * FROM value_events ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<ValueEventEntity>

    @Query("DELETE FROM value_events WHERE habitId = :habitId")
    suspend fun deleteByHabit(habitId: Long)
}

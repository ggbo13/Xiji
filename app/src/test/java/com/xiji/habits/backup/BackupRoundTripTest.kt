package com.xiji.habits.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xiji.habits.data.local.AppDatabase
import com.xiji.habits.data.preferences.SettingsRepository
import com.xiji.habits.data.repository.HabitRepository
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSpec
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 备份往返测试：导出 → 清空 → 导入 → 数据一致；以及非法文件不破坏现有数据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupRoundTripTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: HabitRepository
    private lateinit var manager: BackupManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = HabitRepository(db)
        manager = BackupManager(context, db, repo, SettingsRepository(context))
    }

    @After
    fun tearDown() { db.close() }

    private suspend fun seed(): Long {
        val id = repo.createHabit(
            Habit(
                id = 0, name = "跑步", description = "慢跑", icon = "run", color = 0xFF416B5B,
                categoryId = null, type = HabitType.QUANTITY, targetValue = 5.0, targetUnit = "km",
                schedule = ScheduleSpec.timesPerWeek(3), startDate = LocalDate.of(2026, 1, 1),
                sortOrder = 0, archivedAt = null, confirmQuitDaily = false, createdAt = 0, updatedAt = 0
            )
        )
        repo.setStatus(id, LocalDate.of(2026, 3, 2), RecordStatus.DONE)
        repo.addValue(id, LocalDate.of(2026, 3, 4), 3.0)
        repo.pauseHabit(id, LocalDate.of(2026, 3, 6))
        return id
    }

    @Test
    fun `export then import replace restores everything`() = runTest {
        seed()
        val backup = manager.buildBackup()
        assertEquals(1, backup.habits.size)
        assertEquals(2, backup.records.size)
        assertEquals(1, backup.pauses.size)
        assertEquals(1, backup.scheduleHistory.size)

        // 清空后导入
        db.clearAllTables()
        assertEquals(0, repo.allHabitsOnce().size)

        val result = manager.import(backup, replace = true)
        assertTrue(result is ImportResult.Success)
        assertEquals(1, repo.allHabitsOnce().size)
        assertEquals(2, repo.allRecordsOnce().size)
        assertEquals(1, repo.allPausesOnce().size)
        assertEquals(1, repo.allSnapshotsOnce().size)

        val h = repo.allHabitsOnce().first()
        assertEquals("跑步", h.name)
        assertEquals(HabitType.QUANTITY, h.type)
        assertEquals(3, h.schedule.timesPerWeek)
    }

    @Test
    fun `merge import reassigns ids on conflict`() = runTest {
        seed()
        val backup = manager.buildBackup()
        // 合并到同一份数据上：应新增一份，而不是覆盖
        val result = manager.import(backup, replace = false)
        assertTrue(result is ImportResult.Success)
        assertEquals(2, repo.allHabitsOnce().size)
        assertEquals(4, repo.allRecordsOnce().size)
        // 每条记录都能对应到习惯（外键正确重映射）
        val habitIds = repo.allHabitsOnce().map { it.id }.toSet()
        assertTrue(repo.allRecordsOnce().all { it.habitId in habitIds })
    }

    @Test
    fun `invalid backup is rejected without touching data`() = runTest {
        seed()
        val before = repo.allHabitsOnce().size
        val broken = manager.buildBackup().copy(
            records = listOf(
                BackupRecord(id = 1, habitId = 9999, localDate = "2026-03-01", status = "DONE", accumulatedValue = 1.0, note = null, createdAt = 0, updatedAt = 0)
            )
        )
        val result = manager.import(broken, replace = true)
        assertTrue(result is ImportResult.Failure)
        assertEquals(before, repo.allHabitsOnce().size)
    }

    @Test
    fun `future schema version is rejected`() = runTest {
        seed()
        val future = manager.buildBackup().copy(schemaVersion = 99)
        val result = manager.import(future, replace = true)
        assertTrue(result is ImportResult.Failure)
        assertEquals(1, repo.allHabitsOnce().size)
    }

    @Test
    fun `bad date format is rejected`() = runTest {
        seed()
        val bad = manager.buildBackup().copy(
            records = listOf(BackupRecord(id = 1, habitId = 1, localDate = "2026/03/01", status = "DONE", accumulatedValue = 1.0, note = null, createdAt = 0, updatedAt = 0))
        )
        assertTrue(manager.import(bad, replace = true) is ImportResult.Failure)
        assertEquals(1, repo.allHabitsOnce().size)
    }

    @Test
    fun `preview reports counts`() = runTest {
        seed()
        val backup = manager.buildBackup()
        val preview = manager.preview(backup)
        assertEquals(1, preview.habitCount)
        assertEquals(2, preview.recordCount)
        assertEquals(1, preview.duplicateIds)
    }
}

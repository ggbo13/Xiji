package com.xiji.habits.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xiji.habits.data.local.AppDatabase
import com.xiji.habits.data.repository.HabitRepository
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSpec
import com.xiji.habits.domain.model.ScheduleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 真实 Room（内存库）上的持久化与打卡逻辑测试。
 * 覆盖：新建 → 打卡 → 重复打卡去重 → 撤销 → 数量累加 → 暂停 → 归档保留记录。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RepositoryPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: HabitRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = HabitRepository(db)
    }

    @After
    fun tearDown() { db.close() }

    private fun habit(
        name: String = "阅读",
        type: HabitType = HabitType.COMPLETE,
        target: Double = 1.0,
        schedule: ScheduleSpec = ScheduleSpec.daily()
    ) = Habit(
        id = 0, name = name, description = "", icon = "book", color = 0xFF416B5B,
        categoryId = null, type = type, targetValue = target, targetUnit = "分钟",
        schedule = schedule, startDate = LocalDate.of(2026, 1, 1), sortOrder = 0,
        archivedAt = null, confirmQuitDaily = false, createdAt = 0, updatedAt = 0
    )

    @Test
    fun `create and check in persists`() = runTest {
        val id = repo.createHabit(habit())
        assertTrue(id > 0)
        val today = LocalDate.of(2026, 3, 10)
        repo.setStatus(id, today, RecordStatus.DONE)

        val stored = repo.getRecord(id, today)
        assertNotNull(stored)
        assertEquals(RecordStatus.DONE, stored!!.status)

        // 模拟「重新打开应用」：新的 Repository 读同一个库
        val repo2 = HabitRepository(db)
        val again = repo2.getRecord(id, today)
        assertEquals(RecordStatus.DONE, again?.status)
        assertEquals(1, repo2.allHabitsOnce().size)
    }

    @Test
    fun `repeated check in does not duplicate records`() = runTest {
        val id = repo.createHabit(habit())
        val today = LocalDate.of(2026, 3, 10)
        repeat(5) { repo.setStatus(id, today, RecordStatus.DONE) }
        assertEquals(1, db.recordDao().getAllOnce().size)
    }

    @Test
    fun `undo removes record and stats`() = runTest {
        val id = repo.createHabit(habit())
        val today = LocalDate.of(2026, 3, 10)
        repo.setStatus(id, today, RecordStatus.DONE)
        repo.clearRecord(id, today)
        assertNull(repo.getRecord(id, today))
        assertEquals(0, db.recordDao().getAllOnce().size)
    }

    @Test
    fun `quantity accumulates and marks done at target`() = runTest {
        val id = repo.createHabit(habit(type = HabitType.QUANTITY, target = 30.0))
        val today = LocalDate.of(2026, 3, 10)
        repo.addValue(id, today, 10.0)
        var rec = repo.getRecord(id, today)
        assertEquals(10.0, rec!!.accumulatedValue, 0.001)
        assertEquals(RecordStatus.FAILED, rec.status)

        repo.addValue(id, today, 20.0)
        rec = repo.getRecord(id, today)
        assertEquals(30.0, rec!!.accumulatedValue, 0.001)
        assertEquals(RecordStatus.DONE, rec.status)
        assertEquals(1, db.recordDao().getAllOnce().size)
    }

    @Test
    fun `undo last value is precise`() = runTest {
        val id = repo.createHabit(habit(type = HabitType.QUANTITY, target = 30.0))
        val today = LocalDate.of(2026, 3, 10)
        repo.addValue(id, today, 5.0)
        repo.addValue(id, today, 7.0)
        repo.undoLastValue(id, today)
        assertEquals(5.0, repo.getRecord(id, today)!!.accumulatedValue, 0.001)
        repo.undoLastValue(id, today)
        assertNull(repo.getRecord(id, today))
    }

    @Test
    fun `value input is clamped`() = runTest {
        val id = repo.createHabit(habit(type = HabitType.QUANTITY, target = 10.0))
        val today = LocalDate.of(2026, 3, 10)
        repo.addValue(id, today, 9_999_999.0)
        val rec = repo.getRecord(id, today)!!
        assertTrue(rec.accumulatedValue <= HabitRepository.MAX_VALUE_INPUT)
        assertEquals(RecordStatus.DONE, rec.status)
    }

    @Test
    fun `archive keeps history`() = runTest {
        val id = repo.createHabit(habit())
        val today = LocalDate.of(2026, 3, 10)
        repo.setStatus(id, today, RecordStatus.DONE)
        repo.archiveHabit(id, true)

        val active = repo.habits.first()
        val all = repo.allHabits.first()
        assertEquals(0, active.size)
        assertEquals(1, all.size)
        assertNotNull(repo.getRecord(id, today))
    }

    @Test
    fun `pause and resume`() = runTest {
        val id = repo.createHabit(habit())
        val today = LocalDate.of(2026, 3, 10)
        repo.pauseHabit(id, today)
        assertEquals(1, repo.allPausesOnce().size)
        assertNull(repo.allPausesOnce().first().endDate)
        repo.resumeHabit(id, today.plusDays(3))
        val p = repo.allPausesOnce().first()
        assertEquals(today.plusDays(2), p.endDate)
    }

    @Test
    fun `schedule change writes snapshot without touching history`() = runTest {
        val id = repo.createHabit(habit())
        val before = repo.allSnapshotsOnce()
        assertEquals(1, before.size)

        val h = repo.getHabit(id)!!
        repo.updateHabit(h.copy(schedule = ScheduleSpec(ScheduleType.TIMES_PER_WEEK, timesPerWeek = 4)))
        val after = repo.allSnapshotsOnce()
        assertEquals(2, after.size)
        assertEquals(ScheduleType.DAILY, after.first().schedule.type)
        assertEquals(4, after.last().schedule.timesPerWeek)
    }

    @Test
    fun `reorder persists`() = runTest {
        val a = repo.createHabit(habit("A"))
        val b = repo.createHabit(habit("B"))
        val c = repo.createHabit(habit("C"))
        repo.reorder(listOf(c, a, b))
        assertEquals(listOf("C", "A", "B"), repo.allHabitsOnce().map { it.name })
    }

    @Test
    fun `delete cascades records`() = runTest {
        val id = repo.createHabit(habit())
        repo.setStatus(id, LocalDate.of(2026, 3, 10), RecordStatus.DONE)
        repo.deleteHabit(id)
        assertEquals(0, db.recordDao().getAllOnce().size)
        assertEquals(0, db.snapshotDao().getAllOnce().size)
    }
}

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
import com.xiji.habits.domain.statistics.StatisticsEngine
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackfillTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: HabitRepository
    private val today = LocalDate.now()

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = HabitRepository(db)
    }

    @After
    fun tearDown() { db.close() }

    private suspend fun newHabit(type: HabitType = HabitType.COMPLETE, target: Double = 1.0): Long =
        repo.createHabit(
            Habit(
                id = 0, name = "阅读", description = "", icon = "book", color = 0xFF416B5B,
                categoryId = null, type = type, targetValue = target, targetUnit = "分钟",
                schedule = ScheduleSpec.daily(), startDate = today.minusDays(60), sortOrder = 0,
                archivedAt = null, confirmQuitDaily = false, createdAt = 0, updatedAt = 0
            )
        )

    @Test
    fun `backfill marks past record as backfilled`() = runTest {
        val id = newHabit()
        val past = today.minusDays(3)
        repo.setStatus(id, past, RecordStatus.DONE, backfill = true)
        val rec = repo.getRecord(id, past)!!
        assertEquals(RecordStatus.DONE, rec.status)
        assertTrue(rec.isBackfilled)
    }

    @Test
    fun `today is never marked as backfilled`() = runTest {
        val id = newHabit()
        repo.setStatus(id, today, RecordStatus.DONE, backfill = true)
        assertFalse(repo.getRecord(id, today)!!.isBackfilled)
    }

    @Test
    fun `normal past edit without backfill flag stays unmarked`() = runTest {
        val id = newHabit()
        val past = today.minusDays(2)
        repo.setStatus(id, past, RecordStatus.DONE)
        assertFalse(repo.getRecord(id, past)!!.isBackfilled)
    }

    @Test
    fun `backfill survives subsequent status change`() = runTest {
        val id = newHabit()
        val past = today.minusDays(4)
        repo.setStatus(id, past, RecordStatus.DONE, backfill = true)
        // 之后把状态改成未达成，补签标记应保留（这条记录依然是事后录入的）
        repo.setStatus(id, past, RecordStatus.FAILED)
        assertTrue(repo.getRecord(id, past)!!.isBackfilled)
    }

    @Test
    fun `backfill counts in statistics like a normal completion`() = runTest {
        val id = newHabit()
        val past = today.minusDays(5)
        repo.setStatus(id, past, RecordStatus.DONE, backfill = true)
        val habit = repo.getHabit(id)!!
        val stats = StatisticsEngine.habitStats(
            habit, emptyList(), emptyList(), repo.allRecordsOnce(), today.plusDays(1)
        )
        assertEquals(1, stats.totalCompletions)
    }

    @Test
    fun `quantity backfill via addValue is marked`() = runTest {
        val id = newHabit(HabitType.QUANTITY, 30.0)
        val past = today.minusDays(2)
        repo.addValue(id, past, 30.0)
        val rec = repo.getRecord(id, past)!!
        assertTrue(rec.isBackfilled)
        assertEquals(RecordStatus.DONE, rec.status)
    }

    @Test
    fun `quantity add today is not backfilled`() = runTest {
        val id = newHabit(HabitType.QUANTITY, 30.0)
        repo.addValue(id, today, 10.0)
        assertFalse(repo.getRecord(id, today)!!.isBackfilled)
    }

    @Test
    fun `undoing backfill removes record entirely`() = runTest {
        val id = newHabit()
        val past = today.minusDays(3)
        repo.setStatus(id, past, RecordStatus.DONE, backfill = true)
        repo.clearRecord(id, past)
        assertNull(repo.getRecord(id, past))
        assertEquals(0, repo.allRecordsOnce().size)
    }

    @Test
    fun `backfill keeps note`() = runTest {
        val id = newHabit()
        val past = today.minusDays(6)
        repo.setStatus(id, past, RecordStatus.DONE, note = "出差在外，晚上补读", backfill = true)
        val rec = repo.getRecord(id, past)!!
        assertEquals("出差在外，晚上补读", rec.note)
        assertTrue(rec.isBackfilled)
    }

    @Test
    fun `backfill of future date is rejected by guard`() = runTest {
        val id = newHabit()
        val future = today.plusDays(2)
        // Repository 层不会把未来日期标记为补签
        repo.setStatus(id, future, RecordStatus.DONE, backfill = true)
        assertFalse(repo.getRecord(id, future)!!.isBackfilled)
    }
}

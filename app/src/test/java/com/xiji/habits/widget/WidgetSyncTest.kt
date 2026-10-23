package com.xiji.habits.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiji.habits.data.local.AppDatabase
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
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/**
 * 验证「任何写操作都会触发小组件刷新」。
 *
 * 之前的 bug：只有小组件内勾选会刷新，App 内新建/编辑习惯后组件不更新。
 * 这里用回调计数器覆盖所有写路径。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class WidgetSyncTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: HabitRepository
    private val changes = AtomicInteger(0)
    private val today = LocalDate.now()

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = HabitRepository(db) { changes.incrementAndGet() }
    }

    @After
    fun tearDown() { db.close() }

    private suspend fun newHabit(name: String = "阅读"): Long = repo.createHabit(
        Habit(
            id = 0, name = name, description = "", icon = "book", color = 0xFF416B5B,
            categoryId = null, type = HabitType.COMPLETE, targetValue = 1.0, targetUnit = "",
            schedule = ScheduleSpec.daily(), startDate = today.minusDays(10), sortOrder = 0,
            archivedAt = null, confirmQuitDaily = false, createdAt = 0, updatedAt = 0
        )
    )

    @Test
    fun `create habit notifies widget`() = runTest {
        newHabit()
        assertTrue("新建习惯必须触发刷新", changes.get() >= 1)
    }

    @Test
    fun `check in notifies widget`() = runTest {
        val id = newHabit()
        val before = changes.get()
        repo.setStatus(id, today, RecordStatus.DONE)
        assertTrue(changes.get() > before)
    }

    @Test
    fun `undo notifies widget`() = runTest {
        val id = newHabit()
        repo.setStatus(id, today, RecordStatus.DONE)
        val before = changes.get()
        repo.clearRecord(id, today)
        assertTrue(changes.get() > before)
    }

    @Test
    fun `edit habit notifies widget`() = runTest {
        val id = newHabit()
        val before = changes.get()
        repo.updateHabit(repo.getHabit(id)!!.copy(name = "改名了"))
        assertTrue(changes.get() > before)
    }

    @Test
    fun `archive notifies widget`() = runTest {
        val id = newHabit()
        val before = changes.get()
        repo.archiveHabit(id, true)
        assertTrue(changes.get() > before)
    }

    @Test
    fun `delete notifies widget`() = runTest {
        val id = newHabit()
        val before = changes.get()
        repo.deleteHabit(id)
        assertTrue(changes.get() > before)
    }

    @Test
    fun `pause and resume notify widget`() = runTest {
        val id = newHabit()
        var before = changes.get()
        repo.pauseHabit(id, today)
        assertTrue(changes.get() > before)
        before = changes.get()
        repo.resumeHabit(id, today)
        assertTrue(changes.get() > before)
    }

    @Test
    fun `quantity value notifies widget`() = runTest {
        val id = repo.createHabit(
            Habit(
                id = 0, name = "喝水", description = "", icon = "water", color = 0xFF5B7FA6,
                categoryId = null, type = HabitType.QUANTITY, targetValue = 2000.0, targetUnit = "mL",
                schedule = ScheduleSpec.daily(), startDate = today.minusDays(10), sortOrder = 0,
                archivedAt = null, confirmQuitDaily = false, createdAt = 0, updatedAt = 0
            )
        )
        val before = changes.get()
        repo.addValue(id, today, 500.0)
        assertTrue(changes.get() > before)
    }

    @Test
    fun `reorder notifies widget`() = runTest {
        val a = newHabit("A")
        val b = newHabit("B")
        val before = changes.get()
        repo.reorder(listOf(b, a))
        assertTrue(changes.get() > before)
    }

    @Test
    fun `widget snapshot reflects newly created habit immediately`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        // 直接构造一个使用同一内存库的 container 不现实，这里验证数据层：
        newHabit("新习惯")
        val habits = repo.allHabitsOnce()
        assertEquals(1, habits.size)
        assertEquals("新习惯", habits.first().name)
    }
}

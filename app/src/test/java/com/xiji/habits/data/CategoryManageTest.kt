package com.xiji.habits.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiji.habits.data.local.AppDatabase
import com.xiji.habits.data.repository.HabitRepository
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitCategory
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSpec
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/**
 * 分类管理：删除分类必须保留习惯与打卡记录（只解除关联）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CategoryManageTest {

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

    @Test
    fun `deleting category keeps habits and records`() = runTest {
        val catId = repo.saveCategory(HabitCategory(0, "健康", 0xFF416B5B, 0))
        val habitId = repo.createHabit(
            Habit(
                id = 0, name = "跑步", description = "", icon = "run", color = 0xFF416B5B,
                categoryId = catId, type = HabitType.COMPLETE, targetValue = 1.0, targetUnit = "",
                schedule = ScheduleSpec.daily(), startDate = today.minusDays(7), sortOrder = 0,
                archivedAt = null, confirmQuitDaily = false, createdAt = 0, updatedAt = 0
            )
        )
        repo.setStatus(habitId, today, RecordStatus.DONE)

        repo.deleteCategory(catId)

        // 分类已删除
        assertTrue(repo.allCategoriesOnce().none { it.id == catId })
        // 习惯仍在，但已解除关联
        val h = repo.getHabit(habitId)!!
        assertNull(h.categoryId)
        // 打卡记录完好
        assertEquals(RecordStatus.DONE, repo.getRecord(habitId, today)!!.status)
    }

    @Test
    fun `deleteCategory notifies widget pipeline`() = runTest {
        val catId = repo.saveCategory(HabitCategory(0, "工作", 0xFFB5713F, 0))
        val before = changes.get()
        repo.deleteCategory(catId)
        assertTrue(changes.get() > before)
    }
}

package com.xiji.habits.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiji.habits.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * 验证 v1 → v2 迁移：新增 records.isBackfilled 列，不丢失既有数据，也不丢索引。
 *
 * 手工构造 v1 结构的数据库（建表语句与 app/schemas/.../1.json 一致），写入数据后
 * 通过 FrameworkSQLiteOpenHelper 触发 AppDatabase.MIGRATION_1_2。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun createV1(file: File, withIndex: Boolean = true) {
        file.delete()
        val v1 = SQLiteDatabase.openOrCreateDatabase(file, null)
        v1.execSQL(
            """CREATE TABLE IF NOT EXISTS `habits` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL, `description` TEXT NOT NULL, `icon` TEXT NOT NULL,
                `color` INTEGER NOT NULL, `categoryId` INTEGER, `habitType` TEXT NOT NULL,
                `targetValue` REAL NOT NULL, `targetUnit` TEXT NOT NULL, `scheduleType` TEXT NOT NULL,
                `selectedWeekdays` INTEGER NOT NULL, `timesPerWeek` INTEGER NOT NULL,
                `intervalDays` INTEGER NOT NULL, `anchorDate` INTEGER NOT NULL, `startDate` INTEGER NOT NULL,
                `sortOrder` INTEGER NOT NULL, `archivedAt` INTEGER, `confirmQuitDaily` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"""
        )
        v1.execSQL(
            """CREATE TABLE IF NOT EXISTS `records` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitId` INTEGER NOT NULL,
                `localDate` TEXT NOT NULL, `status` TEXT NOT NULL, `accumulatedValue` REAL NOT NULL,
                `note` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        if (withIndex) {
            v1.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_records_habitId_localDate` ON `records` (`habitId`, `localDate`)"
            )
        }
        v1.execSQL(
            """INSERT INTO habits (id, name, description, icon, color, categoryId, habitType, targetValue,
                targetUnit, scheduleType, selectedWeekdays, timesPerWeek, intervalDays, anchorDate, startDate,
                sortOrder, archivedAt, confirmQuitDaily, createdAt, updatedAt)
               VALUES (1, '阅读', '', 'book', -12227981, NULL, 'COMPLETE', 1.0, '', 'DAILY', 127, 3, 2, 0, 20000, 0, NULL, 0, 100, 100)"""
        )
        v1.execSQL(
            """INSERT INTO records (id, habitId, localDate, status, accumulatedValue, note, createdAt, updatedAt)
               VALUES (1, 1, '2026-10-01', 'DONE', 1.0, '旧记录', 100, 100)"""
        )
        v1.version = 1
        v1.close()
    }

    /** 以 v2 版本打开 v1 数据库，触发 onUpgrade 执行迁移 */
    private fun openAndMigrate(file: File): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(file.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    AppDatabase.MIGRATION_1_2.migrate(db)
                }
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun migrate1To2_keepsDataAndAddsColumn() {
        val file = File(context.cacheDir, "migration-v1.db")
        createV1(file)
        val db = openAndMigrate(file)

        db.query("SELECT name FROM habits WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("阅读", c.getString(0))
        }
        db.query("SELECT note, isBackfilled FROM records WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("旧记录", c.getString(0))
            assertEquals(0, c.getInt(1)) // 旧记录默认不是补签
        }
        db.query("PRAGMA table_info(records)").use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) names.add(c.getString(c.getColumnIndexOrThrow("name")))
            assertTrue("isBackfilled 列应存在", names.contains("isBackfilled"))
        }
        db.close()
        file.delete()
    }

    @Test
    fun migrationKeepsUniqueIndex() {
        val file = File(context.cacheDir, "migration-v1b.db")
        createV1(file)
        val db = openAndMigrate(file)
        db.query("PRAGMA index_list(records)").use { c ->
            val idx = mutableListOf<String>()
            while (c.moveToNext()) idx.add(c.getString(c.getColumnIndexOrThrow("name")))
            assertTrue("唯一索引必须保留", idx.any { it.contains("index_records_habitId_localDate") })
        }
        db.close()
        file.delete()
    }
}

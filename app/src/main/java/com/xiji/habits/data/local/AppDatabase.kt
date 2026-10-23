package com.xiji.habits.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xiji.habits.data.local.dao.CategoryDao
import com.xiji.habits.data.local.dao.HabitDao
import com.xiji.habits.data.local.dao.PauseDao
import com.xiji.habits.data.local.dao.RecordDao
import com.xiji.habits.data.local.dao.ReminderDao
import com.xiji.habits.data.local.dao.SnapshotDao
import com.xiji.habits.data.local.dao.ValueEventDao
import com.xiji.habits.data.local.entity.CategoryEntity
import com.xiji.habits.data.local.entity.HabitEntity
import com.xiji.habits.data.local.entity.PauseEntity
import com.xiji.habits.data.local.entity.RecordEntity
import com.xiji.habits.data.local.entity.ReminderEntity
import com.xiji.habits.data.local.entity.ScheduleSnapshotEntity
import com.xiji.habits.data.local.entity.ValueEventEntity

@Database(
    entities = [
        HabitEntity::class,
        RecordEntity::class,
        CategoryEntity::class,
        ReminderEntity::class,
        PauseEntity::class,
        ScheduleSnapshotEntity::class,
        ValueEventEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun recordDao(): RecordDao
    abstract fun categoryDao(): CategoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun pauseDao(): PauseDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun valueEventDao(): ValueEventDao

    companion object {
        private const val DB_NAME = "xiji.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(*ALL_MIGRATIONS)
                // 不使用破坏性迁移：升级失败时宁可报错，也不清空用户数据
                .build()

        /**
         * v1 → v2：新增 records.isBackfilled（补签标记）。
         * 使用 ALTER TABLE ADD COLUMN，默认值 false，不清空任何现有数据。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE records ADD COLUMN isBackfilled INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** 后续版本在此追加 Migration(n, n+1)；绝不调用 fallbackToDestructiveMigration */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}

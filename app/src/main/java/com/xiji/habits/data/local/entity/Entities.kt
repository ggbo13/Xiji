package com.xiji.habits.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories", indices = [Index("sortOrder")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "habits",
    indices = [Index("categoryId"), Index("sortOrder"), Index("archivedAt")]
)
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val icon: String = "star",
    val color: Long = 0xFF416B5B,
    val categoryId: Long? = null,
    /** COMPLETE / QUANTITY / QUIT */
    val habitType: String = "COMPLETE",
    val targetValue: Double = 1.0,
    val targetUnit: String = "",
    /** DAILY / WEEKDAYS / TIMES_PER_WEEK / INTERVAL */
    val scheduleType: String = "DAILY",
    val selectedWeekdays: Int = 0b1111111,
    val timesPerWeek: Int = 3,
    val intervalDays: Int = 2,
    val anchorDate: Long = 0,
    val startDate: Long,
    val sortOrder: Int = 0,
    val archivedAt: Long? = null,
    val confirmQuitDaily: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["habitId", "localDate"], unique = true),
        Index("localDate")
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    /** ISO-8601 yyyy-MM-dd，按本地日历语义存储，避免时区漂移 */
    val localDate: String,
    /** DONE / FAILED / SKIPPED */
    val status: String = "DONE",
    val accumulatedValue: Double = 0.0,
    val note: String? = null,
    /** 是否为事后补记（补签）。补签不产生额外奖励，但会在日历与历史中明确标注。 */
    val isBackfilled: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("habitId")]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val enabled: Boolean = true,
    val hour: Int = 8,
    val minute: Int = 0,
    val weekdaysMask: Int = 0b1111111
)

@Entity(
    tableName = "pause_periods",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("habitId")]
)
data class PauseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val startDate: Long,
    val endDate: Long? = null,
    val reason: String? = null
)

@Entity(
    tableName = "schedule_history",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["habitId", "effectiveFrom"], unique = true)]
)
data class ScheduleSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val effectiveFrom: Long,
    val scheduleType: String,
    val selectedWeekdays: Int,
    val timesPerWeek: Int,
    val intervalDays: Int,
    val anchorDate: Long,
    val targetValue: Double,
    val targetUnit: String,
    val createdAt: Long
)

/** 数量追加事件，支持精确撤销与审计 */
@Entity(
    tableName = "value_events",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["habitId", "localDate"])]
)
data class ValueEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val localDate: String,
    val delta: Double,
    val createdAt: Long
)

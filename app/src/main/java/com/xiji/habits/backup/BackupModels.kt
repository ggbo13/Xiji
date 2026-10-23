package com.xiji.habits.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 备份文件格式。schemaVersion 用于未来升级迁移。
 * 所有日期使用 ISO-8601 字符串（yyyy-MM-dd），时间戳使用 epoch millis。
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appVersion: String = "1.0.0",
    val exportedAt: Long = 0L,
    val exportedAtIso: String = "",
    val habits: List<BackupHabit> = emptyList(),
    val records: List<BackupRecord> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val reminders: List<BackupReminder> = emptyList(),
    val pauses: List<BackupPause> = emptyList(),
    val scheduleHistory: List<BackupSnapshot> = emptyList(),
    val valueEvents: List<BackupValueEvent> = emptyList(),
    val settings: BackupSettings = BackupSettings()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupHabit(
    val id: Long,
    val name: String,
    val description: String = "",
    val icon: String = "star",
    val color: Long = 0xFF416B5B,
    val categoryId: Long? = null,
    val habitType: String = "COMPLETE",
    val targetValue: Double = 1.0,
    val targetUnit: String = "",
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

@Serializable
data class BackupRecord(
    val id: Long,
    val habitId: Long,
    val localDate: String,
    val status: String = "DONE",
    val accumulatedValue: Double = 0.0,
    val note: String? = null,
    val isBackfilled: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupCategory(val id: Long, val name: String, val color: Long, val sortOrder: Int = 0)

@Serializable
data class BackupReminder(
    val id: Long,
    val habitId: Long,
    val enabled: Boolean = true,
    val hour: Int = 8,
    val minute: Int = 0,
    val weekdaysMask: Int = 0b1111111
)

@Serializable
data class BackupPause(
    val id: Long,
    val habitId: Long,
    val startDate: Long,
    val endDate: Long? = null,
    val reason: String? = null
)

@Serializable
data class BackupSnapshot(
    val id: Long,
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

@Serializable
data class BackupValueEvent(
    val id: Long,
    val habitId: Long,
    val localDate: String,
    val delta: Double,
    val createdAt: Long
)

@Serializable
data class BackupSettings(
    val themeMode: String = "SYSTEM",
    val accent: String = "forest",
    val dynamicColor: Boolean = false,
    val weekStart: String = "monday",
    val reduceAnimation: Boolean = false,
    val language: String = "zh"
)

/** 导入预览 */
data class ImportPreview(
    val schemaVersion: Int,
    val habitCount: Int,
    val recordCount: Int,
    val categoryCount: Int,
    val reminderCount: Int,
    val exportedAtIso: String,
    val appVersion: String,
    val duplicateIds: Int
)

sealed class ImportResult {
    data class Success(val habits: Int, val records: Int) : ImportResult()
    data class Failure(val reason: String) : ImportResult()
}

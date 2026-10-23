package com.xiji.habits.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.xiji.habits.data.local.AppDatabase
import com.xiji.habits.data.local.entity.CategoryEntity
import com.xiji.habits.data.local.entity.HabitEntity
import com.xiji.habits.data.local.entity.PauseEntity
import com.xiji.habits.data.local.entity.RecordEntity
import com.xiji.habits.data.local.entity.ReminderEntity
import com.xiji.habits.data.local.entity.ScheduleSnapshotEntity
import com.xiji.habits.data.local.entity.ValueEventEntity
import com.xiji.habits.data.preferences.SettingsRepository
import com.xiji.habits.data.repository.HabitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 本地备份与恢复。所有文件通过 SAF 由用户主动选择位置，
 * App 自身不发起任何网络请求，也不申请存储权限。
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val repo: HabitRepository,
    private val settingsRepo: SettingsRepository
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // ---------- 导出 ----------

    suspend fun buildBackup(): BackupFile = withContext(Dispatchers.IO) {
        val habits = db.habitDao().getAllOnce()
        val records = db.recordDao().getAllOnce()
        val categories = db.categoryDao().getAllOnce()
        val reminders = db.reminderDao().getAllOnce()
        val pauses = db.pauseDao().getAllOnce()
        val snapshots = db.snapshotDao().getAllOnce()
        val events = db.valueEventDao().getAllOnce()
        val settings = settingsRepo.settings.first()

        BackupFile(
            schemaVersion = BackupFile.CURRENT_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            exportedAtIso = Instant.now().toString(),
            habits = habits.map {
                BackupHabit(
                    id = it.id, name = it.name, description = it.description, icon = it.icon, color = it.color,
                    categoryId = it.categoryId, habitType = it.habitType, targetValue = it.targetValue,
                    targetUnit = it.targetUnit, scheduleType = it.scheduleType, selectedWeekdays = it.selectedWeekdays,
                    timesPerWeek = it.timesPerWeek, intervalDays = it.intervalDays, anchorDate = it.anchorDate,
                    startDate = it.startDate, sortOrder = it.sortOrder, archivedAt = it.archivedAt,
                    confirmQuitDaily = it.confirmQuitDaily, createdAt = it.createdAt, updatedAt = it.updatedAt
                )
            },
            records = records.map {
                BackupRecord(
                    id = it.id, habitId = it.habitId, localDate = it.localDate, status = it.status,
                    accumulatedValue = it.accumulatedValue, note = it.note,
                    isBackfilled = it.isBackfilled, createdAt = it.createdAt, updatedAt = it.updatedAt
                )
            },
            categories = categories.map { BackupCategory(it.id, it.name, it.color, it.sortOrder) },
            reminders = reminders.map {
                BackupReminder(it.id, it.habitId, it.enabled, it.hour, it.minute, it.weekdaysMask)
            },
            pauses = pauses.map { BackupPause(it.id, it.habitId, it.startDate, it.endDate, it.reason) },
            scheduleHistory = snapshots.map {
                BackupSnapshot(
                    it.id, it.habitId, it.effectiveFrom, it.scheduleType, it.selectedWeekdays,
                    it.timesPerWeek, it.intervalDays, it.anchorDate, it.targetValue, it.targetUnit, it.createdAt
                )
            },
            valueEvents = events.map { BackupValueEvent(it.id, it.habitId, it.localDate, it.delta, it.createdAt) },
            settings = BackupSettings(
                themeMode = settings.themeMode.name,
                accent = settings.accentKey,
                dynamicColor = settings.dynamicColor,
                weekStart = settings.weekStart.key,
                reduceAnimation = settings.reduceAnimation,
                language = settings.language
            )
        )
    }

    suspend fun exportJson(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val backup = buildBackup()
            val text = json.encodeToString(BackupFile.serializer(), backup)
            context.contentResolver.openOutputStream(uri)?.use { out: OutputStream ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: error("无法写入所选位置")
            backup.records.size
        }
    }

    suspend fun exportCsv(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val habits = db.habitDao().getAllOnce().associateBy { it.id }
            val records = db.recordDao().getAllOnce()
            val sb = StringBuilder()
            sb.append("habit_id,habit_name,date,status,value,unit,note\n")
            records.sortedBy { it.localDate }.forEach { r ->
                val h = habits[r.habitId]
                sb.append(r.habitId).append(',')
                sb.append(csvEscape(h?.name ?: "")).append(',')
                sb.append(r.localDate).append(',')
                sb.append(r.status).append(',')
                sb.append(r.accumulatedValue).append(',')
                sb.append(csvEscape(h?.targetUnit ?: "")).append(',')
                sb.append(csvEscape(r.note ?: "")).append('\n')
            }
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(sb.toString().toByteArray(Charsets.UTF_8))
                it.flush()
            } ?: error("无法写入所选位置")
            records.size
        }
    }

    private fun csvEscape(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n')) "\"" + v.replace("\"", "\"\"") + "\"" else v

    // ---------- 导入 ----------

    suspend fun parse(uri: Uri): Result<BackupFile> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: error("无法读取所选文件")
            if (text.isBlank()) error("文件为空")
            val file = json.decodeFromString(BackupFile.serializer(), text)
            validate(file)
            file
        }
    }

    private fun validate(file: BackupFile) {
        require(file.schemaVersion in 1..BackupFile.CURRENT_SCHEMA_VERSION) {
            "不支持的备份版本：${file.schemaVersion}"
        }
        require(file.habits.size <= 5000) { "习惯数量异常" }
        require(file.records.size <= 500_000) { "记录数量异常" }
        val habitIds = file.habits.map { it.id }.toSet()
        val orphan = file.records.count { it.habitId !in habitIds }
        require(orphan == 0) { "存在 $orphan 条记录找不到对应习惯，文件可能已损坏" }
        file.records.forEach { r ->
            runCatching { LocalDate.parse(r.localDate) }.getOrElse { error("日期格式错误：${r.localDate}") }
            require(r.status in setOf("DONE", "FAILED", "SKIPPED")) { "未知的记录状态：${r.status}" }
        }
        file.habits.forEach {
            require(it.name.isNotBlank()) { "存在没有名称的习惯" }
            require(it.habitType in setOf("COMPLETE", "QUANTITY", "QUIT")) { "未知的习惯类型" }
            require(it.scheduleType in setOf("DAILY", "WEEKDAYS", "TIMES_PER_WEEK", "INTERVAL")) { "未知的周期类型" }
        }
    }

    suspend fun preview(file: BackupFile): ImportPreview {
        val existing = runCatching { db.habitDao().getAllOnce().map { it.id }.toSet() }.getOrDefault(emptySet())
        val dup = file.habits.count { it.id in existing }
        return ImportPreview(
            schemaVersion = file.schemaVersion,
            habitCount = file.habits.size,
            recordCount = file.records.size,
            categoryCount = file.categories.size,
            reminderCount = file.reminders.size,
            exportedAtIso = file.exportedAtIso,
            appVersion = file.appVersion,
            duplicateIds = dup
        )
    }

    /**
     * 导入。replace=true 时整体替换（先清空再写入），false 时合并（ID 冲突则重新分配）。
     * 全部写入在单个事务中完成，任何异常都会回滚，不会破坏现有数据。
     */
    suspend fun import(file: BackupFile, replace: Boolean): ImportResult = withContext(Dispatchers.IO) {
        try {
            validate(file)
            var habitCount = 0
            var recordCount = 0
            db.withTransaction {
                if (replace) {
                    db.clearAllTables()
                }
                val idMap = HashMap<Long, Long>()

                file.categories.forEach { c ->
                    val existingIds = db.categoryDao().getAllOnce().map { it.id }.toSet()
                    val newId = if (!replace && c.id in existingIds) 0L else c.id
                    val assigned = db.categoryDao().upsert(
                        CategoryEntity(id = newId, name = c.name, color = c.color, sortOrder = c.sortOrder)
                    )
                    if (newId == 0L) idMap[-c.id] = assigned
                }

                val existingHabits = db.habitDao().getAllOnce().map { it.id }.toSet()
                var order = db.habitDao().nextSortOrder()
                file.habits.forEach { h ->
                    val conflict = !replace && h.id in existingHabits
                    val entity = HabitEntity(
                        id = if (conflict) 0 else h.id,
                        name = h.name, description = h.description, icon = h.icon, color = h.color,
                        categoryId = h.categoryId?.let { cid -> if (conflict) null else cid },
                        habitType = h.habitType, targetValue = h.targetValue, targetUnit = h.targetUnit,
                        scheduleType = h.scheduleType, selectedWeekdays = h.selectedWeekdays,
                        timesPerWeek = h.timesPerWeek, intervalDays = h.intervalDays,
                        anchorDate = h.anchorDate, startDate = h.startDate,
                        sortOrder = if (conflict) order++ else h.sortOrder,
                        archivedAt = h.archivedAt, confirmQuitDaily = h.confirmQuitDaily,
                        createdAt = h.createdAt, updatedAt = h.updatedAt
                    )
                    val assigned = db.habitDao().insert(entity)
                    idMap[h.id] = assigned
                    habitCount++
                }

                file.records.forEach { r ->
                    val mapped = idMap[r.habitId] ?: return@forEach
                    db.recordDao().upsert(
                        RecordEntity(
                            id = if (replace) r.id else 0,
                            habitId = mapped, localDate = r.localDate, status = r.status,
                            accumulatedValue = r.accumulatedValue, note = r.note,
                            isBackfilled = r.isBackfilled,
                            createdAt = r.createdAt, updatedAt = r.updatedAt
                        )
                    )
                    recordCount++
                }
                file.reminders.forEach { rem ->
                    val mapped = idMap[rem.habitId] ?: return@forEach
                    db.reminderDao().insert(
                        ReminderEntity(
                            id = 0, habitId = mapped, enabled = rem.enabled,
                            hour = rem.hour, minute = rem.minute, weekdaysMask = rem.weekdaysMask
                        )
                    )
                }
                file.pauses.forEach { p ->
                    val mapped = idMap[p.habitId] ?: return@forEach
                    db.pauseDao().insert(
                        PauseEntity(0, mapped, p.startDate, p.endDate, p.reason)
                    )
                }
                file.scheduleHistory.forEach { s ->
                    val mapped = idMap[s.habitId] ?: return@forEach
                    db.snapshotDao().insert(
                        ScheduleSnapshotEntity(
                            0, mapped, s.effectiveFrom, s.scheduleType, s.selectedWeekdays,
                            s.timesPerWeek, s.intervalDays, s.anchorDate, s.targetValue, s.targetUnit, s.createdAt
                        )
                    )
                }
                file.valueEvents.forEach { e ->
                    val mapped = idMap[e.habitId] ?: return@forEach
                    db.valueEventDao().insert(
                        ValueEventEntity(0, mapped, e.localDate, e.delta, e.createdAt)
                    )
                }
            }
            repo.notifyImported()
            ImportResult.Success(habitCount, recordCount)
        } catch (t: Throwable) {
            ImportResult.Failure(t.message ?: "导入失败，已回滚，现有数据未受影响")
        }
    }

    /** 建议的文件名 */
    fun defaultFileName(ext: String): String {
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        return "xiji-backup-$date.$ext"
    }
}

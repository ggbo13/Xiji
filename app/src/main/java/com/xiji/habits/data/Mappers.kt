package com.xiji.habits.data

import com.xiji.habits.data.local.entity.CategoryEntity
import com.xiji.habits.data.local.entity.HabitEntity
import com.xiji.habits.data.local.entity.PauseEntity
import com.xiji.habits.data.local.entity.RecordEntity
import com.xiji.habits.data.local.entity.ReminderEntity
import com.xiji.habits.data.local.entity.ScheduleSnapshotEntity
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.HabitCategory
import com.xiji.habits.domain.model.HabitPausePeriod
import com.xiji.habits.domain.model.HabitRecord
import com.xiji.habits.domain.model.HabitReminder
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.ScheduleSnapshot
import com.xiji.habits.domain.model.ScheduleSpec
import com.xiji.habits.domain.model.ScheduleType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun LocalDate.toIso(): String = format(ISO)
fun String.toLocalDate(): LocalDate = LocalDate.parse(this, ISO)
fun Long.toLocalDateFromEpochDay(): LocalDate = LocalDate.ofEpochDay(this)
fun LocalDate.toEpochDayLong(): Long = toEpochDay()

fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    description = description,
    icon = icon,
    color = color,
    categoryId = categoryId,
    type = runCatching { HabitType.valueOf(habitType) }.getOrDefault(HabitType.COMPLETE),
    targetValue = targetValue,
    targetUnit = targetUnit,
    schedule = ScheduleSpec(
        type = runCatching { ScheduleType.valueOf(scheduleType) }.getOrDefault(ScheduleType.DAILY),
        weekdaysMask = selectedWeekdays,
        timesPerWeek = timesPerWeek,
        intervalDays = intervalDays,
        anchorDate = LocalDate.ofEpochDay(anchorDate)
    ),
    startDate = LocalDate.ofEpochDay(startDate),
    sortOrder = sortOrder,
    archivedAt = archivedAt,
    confirmQuitDaily = confirmQuitDaily,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    description = description,
    icon = icon,
    color = color,
    categoryId = categoryId,
    habitType = type.name,
    targetValue = targetValue,
    targetUnit = targetUnit,
    scheduleType = schedule.type.name,
    selectedWeekdays = schedule.weekdaysMask,
    timesPerWeek = schedule.timesPerWeek,
    intervalDays = schedule.intervalDays,
    anchorDate = schedule.anchorDate.toEpochDay(),
    startDate = startDate.toEpochDay(),
    sortOrder = sortOrder,
    archivedAt = archivedAt,
    confirmQuitDaily = confirmQuitDaily,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun RecordEntity.toDomain(): HabitRecord = HabitRecord(
    id = id,
    habitId = habitId,
    date = localDate.toLocalDate(),
    status = runCatching { RecordStatus.valueOf(status) }.getOrDefault(RecordStatus.DONE),
    accumulatedValue = accumulatedValue,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isBackfilled = isBackfilled
)

fun HabitRecord.toEntity(): RecordEntity = RecordEntity(
    id = id,
    habitId = habitId,
    localDate = date.toIso(),
    status = status.name,
    accumulatedValue = accumulatedValue,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isBackfilled = isBackfilled
)

fun CategoryEntity.toDomain(): HabitCategory = HabitCategory(id, name, color, sortOrder)

fun ReminderEntity.toDomain(): HabitReminder =
    HabitReminder(id, habitId, enabled, hour, minute, weekdaysMask)

fun PauseEntity.toDomain(): HabitPausePeriod = HabitPausePeriod(
    id = id,
    habitId = habitId,
    startDate = LocalDate.ofEpochDay(startDate),
    endDate = endDate?.let { LocalDate.ofEpochDay(it) },
    reason = reason
)

fun ScheduleSnapshotEntity.toDomain(): ScheduleSnapshot = ScheduleSnapshot(
    id = id,
    habitId = habitId,
    effectiveFrom = LocalDate.ofEpochDay(effectiveFrom),
    schedule = ScheduleSpec(
        type = runCatching { ScheduleType.valueOf(scheduleType) }.getOrDefault(ScheduleType.DAILY),
        weekdaysMask = selectedWeekdays,
        timesPerWeek = timesPerWeek,
        intervalDays = intervalDays,
        anchorDate = LocalDate.ofEpochDay(anchorDate)
    ),
    targetValue = targetValue,
    targetUnit = targetUnit,
    createdAt = createdAt
)

fun ScheduleSnapshot.toEntity(): ScheduleSnapshotEntity = ScheduleSnapshotEntity(
    id = id,
    habitId = habitId,
    effectiveFrom = effectiveFrom.toEpochDay(),
    scheduleType = schedule.type.name,
    selectedWeekdays = schedule.weekdaysMask,
    timesPerWeek = schedule.timesPerWeek,
    intervalDays = schedule.intervalDays,
    anchorDate = schedule.anchorDate.toEpochDay(),
    targetValue = targetValue,
    targetUnit = targetUnit,
    createdAt = createdAt
)

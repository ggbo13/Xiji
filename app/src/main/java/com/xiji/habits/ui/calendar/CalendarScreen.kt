package com.xiji.habits.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.formatValue
import com.xiji.habits.ui.components.CheckmarkButton
import com.xiji.habits.ui.components.HabitIconBadge
import com.xiji.habits.ui.components.MonthCalendar
import com.xiji.habits.ui.components.PillLabel
import com.xiji.habits.ui.components.XijiCard
import com.xiji.habits.ui.theme.LocalXijiColors
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onSetStatus: (Habit, LocalDate, RecordStatus) -> Unit,
    onClear: (Habit, LocalDate) -> Unit,
    onAddValue: (Habit, LocalDate, Double) -> Unit,
    onOpenHabit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onToggleBackfillMode: () -> Unit = {},
    onBackfill: (Habit, LocalDate) -> Unit = { _, _ -> },
    onBackfillAll: (LocalDate) -> Unit = {},
    onEditEntry: (CalendarDayEntry) -> Unit = {}
) {
    val colors = LocalXijiColors.current
    Box(modifier.fillMaxSize().background(colors.pageBackground)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                    Text("日历", style = MaterialTheme.typography.headlineMedium, color = colors.primaryText)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "回看每一天，也允许有休息的日子。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                XijiCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onPrev) {
                                Icon(Icons.Rounded.ChevronLeft, "上个月", tint = colors.primaryText)
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "${state.month.year} 年 ${state.month.monthValue} 月",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.primaryText
                                )
                                if (state.monthScheduled > 0) {
                                    Text(
                                        "完成 ${state.monthDone} / ${state.monthScheduled}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        color = colors.secondaryText
                                    )
                                }
                            }
                            IconButton(onClick = onNext) {
                                Icon(Icons.Rounded.ChevronRight, "下个月", tint = colors.primaryText)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        MonthCalendar(
                            month = state.month,
                            today = state.today,
                            selected = state.selectedDate,
                            stateFor = { state.monthState[it] },
                            onClick = onSelectDate
                        )
                        Spacer(Modifier.height(12.dp))
                        CalendarLegend()
                    }
                }
            }
            item {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.selectedDate.monthValue} 月 ${state.selectedDate.dayOfMonth} 日",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.primaryText
                    )
                    Spacer(Modifier.width(8.dp))
                    if (state.selectedDate == state.today) {
                        PillLabel("今天", color = MaterialTheme.colorScheme.primary, container = MaterialTheme.colorScheme.primaryContainer)
                    }
                    Spacer(Modifier.weight(1f))
                    if (state.dayEntries.isNotEmpty()) {
                        Text(
                            "${(state.dayRate * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = colors.secondaryText
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    if (state.selectedDate.isBefore(state.today)) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (state.backfillMode) MaterialTheme.colorScheme.primaryContainer
                                    else colors.subtleFill
                                )
                                .clickable(onClick = onToggleBackfillMode)
                                .padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "补签",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.backfillMode) MaterialTheme.colorScheme.primary
                                else colors.secondaryText
                            )
                        }
                    }
                }
                if (state.backfillMode && state.selectedDate.isBefore(state.today)) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.subtleFill)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "补签模式：把这一天补记为已完成",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "全部补签",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onBackfillAll(state.selectedDate) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (state.dayEntries.isEmpty()) {
                item {
                    XijiCard(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                            Text("这一天没有安排的习惯", style = MaterialTheme.typography.bodyMedium, color = colors.secondaryText)
                        }
                    }
                }
            } else {
                items(state.dayEntries, key = { it.habit.id }, contentType = { "calendar-entry" }) { entry ->
                    DayEntryCard(
                        entry = entry,
                        date = state.selectedDate,
                        backfillMode = state.backfillMode && state.selectedDate.isBefore(state.today),
                        onSetStatus = onSetStatus,
                        onClear = onClear,
                        onAddValue = onAddValue,
                        onOpenHabit = onOpenHabit,
                        onBackfill = onBackfill,
                        onEditEntry = onEditEntry
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun CalendarLegend() {
    val colors = LocalXijiColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(MaterialTheme.colorScheme.primary, "完成")
        LegendItem(colors.danger, "未达成")
        LegendItem(colors.cardBorder, "计划中", hollow = true)
        LegendItem(colors.secondaryText, "跳过")
    }
}

@Composable
private fun LegendItem(color: Color, label: String, hollow: Boolean = false) {
    val colors = LocalXijiColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (hollow) Color.Transparent else color)
                .then(if (hollow) Modifier.background(colors.subtleFill) else Modifier)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
    }
}

@Composable
private fun DayEntryCard(
    entry: CalendarDayEntry,
    date: LocalDate,
    backfillMode: Boolean,
    onSetStatus: (Habit, LocalDate, RecordStatus) -> Unit,
    onClear: (Habit, LocalDate) -> Unit,
    onAddValue: (Habit, LocalDate, Double) -> Unit,
    onOpenHabit: (Long) -> Unit,
    onBackfill: (Habit, LocalDate) -> Unit,
    onEditEntry: (CalendarDayEntry) -> Unit
) {
    val colors = LocalXijiColors.current
    val habit = entry.habit
    val accent = Color(habit.color)
    val done = entry.state == DayState.DONE
    val isFuture = date.isAfter(LocalDate.now())
    XijiCard(Modifier.fillMaxWidth(), onClick = { onOpenHabit(habit.id) }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            HabitIconBadge(habit.icon, habit.color, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (entry.state) {
                        DayState.PAUSED -> "暂停期间"
                        DayState.SKIPPED -> "已跳过"
                        DayState.FAILED -> if (habit.isQuantity && (entry.record?.accumulatedValue ?: 0.0) > 0)
                            "未达标 · ${formatValue(entry.record!!.accumulatedValue)}/${formatValue(entry.targetValue)}${habit.targetUnit}"
                        else "未达成"
                        DayState.PARTIAL -> "进行中 · ${formatValue(entry.record?.accumulatedValue ?: 0.0)}/${formatValue(entry.targetValue)}${habit.targetUnit}"
                        DayState.DONE -> if (habit.isQuantity && (entry.record?.accumulatedValue ?: 0.0) > 0)
                            "完成 · ${formatValue(entry.record!!.accumulatedValue)}${habit.targetUnit}" else "已完成"
                        DayState.PENDING -> "未记录"
                        else -> "非计划日期"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
                if (entry.record?.isBackfilled == true) {
                    Spacer(Modifier.width(6.dp))
                    PillLabel("补签", color = colors.warning, container = colors.subtleFill)
                }
                }
            }
            when {
                entry.state == DayState.PAUSED -> PillLabel("暂停", color = colors.secondaryText, container = colors.subtleFill)
                backfillMode && entry.state != DayState.DONE -> {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { onBackfill(habit, date) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "补签",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                habit.isQuantity && !done -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallAction("−") { onAddValue(habit, date, -1.0) }
                        Spacer(Modifier.width(6.dp))
                        SmallAction("+") { onAddValue(habit, date, 1.0) }
                    }
                }
                else -> CheckmarkButton(
                    checked = done,
                    onToggle = {
                        if (done) onClear(habit, date)
                        else onSetStatus(habit, date, RecordStatus.DONE)
                    },
                    accent = accent,
                    size = 34.dp,
                    enabled = !isFuture,
                    contentDescription = "${habit.name} ${if (done) "已完成，点击撤销" else "标记完成"}"
                )
            }
        }
        if (entry.record?.note?.isNotBlank() == true) {
            Text(
                "备注：${entry.record.note}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondaryText,
                modifier = Modifier.padding(start = 64.dp, end = 14.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    val colors = LocalXijiColors.current
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.subtleFill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = colors.primaryText)
    }
}

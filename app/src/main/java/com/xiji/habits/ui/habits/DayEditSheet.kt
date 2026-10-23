package com.xiji.habits.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.formatValue
import com.xiji.habits.ui.components.HabitIconBadge
import com.xiji.habits.ui.components.PillLabel
import com.xiji.habits.ui.theme.LocalXijiColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 单日记录编辑 / 补签面板。
 *
 * 语义：
 * - 编辑「今天」= 正常打卡；
 * - 编辑「今天以前」= 补签（backfill），会记录 isBackfilled 并在日历与历史中标注；
 * - 未来日期不可编辑（面板不会打开）。
 *
 * 补签不给任何额外奖励，也不改变连续/完成率的计算口径——它只是把已经发生的事实记下来。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditSheet(
    item: DayDetailItem,
    date: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSetStatus: (RecordStatus) -> Unit,
    onClear: () -> Unit,
    onAddValue: (Double) -> Unit,
    onUndoValue: () -> Unit,
    onSetNote: (String) -> Unit
) {
    val colors = LocalXijiColors.current
    val habit = item.habit
    var note by remember(date, habit.id) { mutableStateOf(item.record?.note ?: "") }
    var valueInput by remember(date, habit.id) { mutableStateOf("") }

    val isPast = date.isBefore(today)
    val daysAgo = ChronoUnit.DAYS.between(date, today)
    val alreadyBackfilled = item.record?.isBackfilled == true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitIconBadge(habit.icon, habit.color, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(habit.name, style = MaterialTheme.typography.titleMedium, color = colors.primaryText)
                    Text(
                        buildString {
                            append("${date.year}年${date.monthValue}月${date.dayOfMonth}日")
                            append(" · ")
                            append(stateLabel(item.state))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryText
                    )
                }
                if (alreadyBackfilled) {
                    PillLabel("已补签", color = colors.warning, container = colors.subtleFill)
                }
            }

            if (isPast) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.subtleFill)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        tint = colors.warning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (daysAgo == 1L) "补签：记录昨天的情况"
                        else "补签：记录 $daysAgo 天前的情况",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primaryText
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "补签只是补记事实，不会带来额外奖励，也不会改写已完成的历史。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("状态", style = MaterialTheme.typography.labelMedium, color = colors.secondaryText)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("完成", item.state == DayState.DONE, colors.primaryText) { onSetStatus(RecordStatus.DONE) }
                StatusChip(
                    if (habit.isQuit) "破戒" else "未达成",
                    item.state == DayState.FAILED,
                    colors.primaryText
                ) { onSetStatus(RecordStatus.FAILED) }
                StatusChip("跳过", item.state == DayState.SKIPPED, colors.primaryText) { onSetStatus(RecordStatus.SKIPPED) }
                StatusChip("清除", false, colors.danger) { onClear() }
            }

            if (habit.isQuantity) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "数量：${formatValue(item.record?.accumulatedValue ?: 0.0)} / ${formatValue(item.targetValue)}${habit.targetUnit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primaryText
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = valueInput,
                        onValueChange = { valueInput = it.filter { c -> c.isDigit() || c == '.' }.take(8) },
                        label = { Text("追加") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = colors.cardBorder,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        valueInput.toDoubleOrNull()?.takeIf { it > 0 }?.let { onAddValue(it) }
                        valueInput = ""
                    }) { Text("添加") }
                    TextButton(onClick = onUndoValue) { Text("撤销一步") }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                label = { Text(if (isPast) "补签备注" else "当天备注") },
                placeholder = { Text("可选，例如：出差在外，晚上补读了两章") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = colors.cardBorder,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onSetNote(note); onDismiss() }) {
                    Text("保存备注", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun stateLabel(state: DayState): String = when (state) {
    DayState.DONE -> "已完成"
    DayState.FAILED -> "未达成"
    DayState.SKIPPED -> "已跳过"
    DayState.PARTIAL -> "进行中"
    DayState.PAUSED -> "暂停"
    DayState.PENDING -> "未记录"
    DayState.FUTURE -> "未来"
    DayState.NOT_SCHEDULED -> "非计划"
}

@Composable
private fun StatusChip(label: String, selected: Boolean, contentColor: Color, onClick: () -> Unit) {
    val colors = LocalXijiColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else colors.subtleFill)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else contentColor
        )
    }
}

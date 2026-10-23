package com.xiji.habits.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.formatValue
import com.xiji.habits.ui.components.HabitIconBadge
import com.xiji.habits.ui.theme.LocalXijiColors
import java.time.LocalDate

/**
 * 历史记录编辑面板：可修改某一天的状态、数量与备注。
 * 只允许编辑今天及以前的日期。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditSheet(
    item: DayDetailItem,
    date: LocalDate,
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitIconBadge(habit.icon, habit.color, size = 38.dp)
                Spacer(Modifier.padding(end = 12.dp))
                Column {
                    Text(habit.name, style = MaterialTheme.typography.titleMedium, color = colors.primaryText)
                    Text(
                        "${date.year}年${date.monthValue}月${date.dayOfMonth}日 · ${stateLabel(item.state)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryText
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("状态", style = MaterialTheme.typography.labelMedium, color = colors.secondaryText)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("完成", item.state == DayState.DONE) { onSetStatus(RecordStatus.DONE) }
                if (habit.isQuit) {
                    StatusChip("破戒", item.state == DayState.FAILED) { onSetStatus(RecordStatus.FAILED) }
                } else {
                    StatusChip("未达成", item.state == DayState.FAILED) { onSetStatus(RecordStatus.FAILED) }
                }
                StatusChip("跳过", item.state == DayState.SKIPPED) { onSetStatus(RecordStatus.SKIPPED) }
                StatusChip("清除", false) { onClear() }
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
                        shape = RoundedCornerShape(12.dp),
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
                label = { Text("当天备注") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onSetNote(note); onDismiss() }) { Text("保存备注") }
            }
            Text(
                "补签不会带来额外奖励，也不会影响已完成的连续记录之外的计算口径。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondaryText
            )
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
private fun StatusChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalXijiColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else colors.subtleFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else colors.secondaryText
        )
    }
}

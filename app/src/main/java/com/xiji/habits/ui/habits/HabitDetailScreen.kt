package com.xiji.habits.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.RecordStatus
import com.xiji.habits.domain.model.formatValue
import com.xiji.habits.ui.components.CheckmarkButton
import com.xiji.habits.ui.components.CompletionTrendChart
import com.xiji.habits.ui.components.HabitHeatmap
import com.xiji.habits.ui.components.HabitIconBadge
import com.xiji.habits.ui.components.HairlineDivider
import com.xiji.habits.ui.components.HeatmapLegend
import com.xiji.habits.ui.components.MonthCalendar
import com.xiji.habits.ui.components.PillLabel
import com.xiji.habits.ui.components.SmoothProgressBar
import com.xiji.habits.ui.components.StatNumber
import com.xiji.habits.ui.components.XijiCard
import com.xiji.habits.ui.theme.LocalXijiColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun HabitDetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onToggleToday: () -> Unit,
    onSetStatus: (LocalDate, RecordStatus) -> Unit,
    onClear: (LocalDate) -> Unit,
    onAddValue: (LocalDate, Double) -> Unit,
    onUndoValue: (LocalDate) -> Unit,
    onSetNote: (LocalDate, String) -> Unit,
    onTogglePause: () -> Unit,
    onArchive: () -> Unit,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onConsumeSnack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalXijiColors.current
    val habit = state.habit
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<DayDetailItem?>(null) }

    LaunchedEffect(state.snack) {
        state.snack?.let {
            snackbarHostState.showSnackbar(it, duration = androidx.compose.material3.SnackbarDuration.Short)
            onConsumeSnack()
        }
    }

    Box(modifier.fillMaxSize().background(colors.pageBackground)) {
        if (habit == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("习惯不存在或已删除", color = colors.secondaryText)
            }
        } else {
            val accent = Color(habit.color)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)
            ) {
                item {
                    Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = colors.primaryText)
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = onEdit) {
                                Icon(Icons.Rounded.Edit, "编辑习惯", tint = colors.primaryText)
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Rounded.MoreVert, "更多操作", tint = colors.primaryText)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (state.paused) "恢复习惯" else "暂停习惯") },
                                        leadingIcon = {
                                            Icon(
                                                if (state.paused) Icons.Rounded.PlayCircle else Icons.Rounded.PauseCircle,
                                                null
                                            )
                                        },
                                        onClick = { menuOpen = false; onTogglePause() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("归档习惯") },
                                        leadingIcon = { Icon(Icons.Rounded.Inventory2, null) },
                                        onClick = { menuOpen = false; onArchive() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除习惯", color = colors.danger) },
                                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = colors.danger) },
                                        onClick = { menuOpen = false; onRequestDelete() }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HabitIconBadge(habit.icon, habit.color, size = 54.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    habit.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.primaryText
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    habit.targetLabel(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                        }
                        if (habit.description.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(habit.description, style = MaterialTheme.typography.bodyMedium, color = colors.secondaryText)
                        }
                        if (state.paused) {
                            Spacer(Modifier.height(10.dp))
                            PillLabel("暂停中 · 不计入完成率", color = colors.warning, container = colors.subtleFill)
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    XijiCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatBlock("当前连续", "${state.stats?.currentStreak ?: 0}", state.stats?.streakUnit ?: "天", Modifier.weight(1f))
                                StatBlock("最长连续", "${state.stats?.longestStreak ?: 0}", state.stats?.streakUnit ?: "天", Modifier.weight(1f))
                                StatBlock("累计完成", "${state.stats?.totalCompletions ?: 0}", "次", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(16.dp))
                            HairlineDivider()
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("习迹强度", style = MaterialTheme.typography.labelMedium, color = colors.secondaryText)
                                    Spacer(Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        StatNumber("${state.stats?.strength ?: 0}")
                                        Text(" / 100", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    SmoothProgressBar(
                                        progress = (state.stats?.strength ?: 0) / 100f,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = accent,
                                        height = 6.dp
                                    )
                                }
                                Spacer(Modifier.width(18.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("完成率", style = MaterialTheme.typography.labelMedium, color = colors.secondaryText)
                                    Spacer(Modifier.height(6.dp))
                                    StatNumber("${((state.stats?.overallRate ?: 0f) * 100).toInt()}%")
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "强度按最近 90 天加权计算，偶尔遗漏不会清零。",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    XijiCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("本月", style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${(state.monthRate * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.secondaryText
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            MonthCalendar(
                                month = state.month,
                                today = LocalDate.now(),
                                selected = state.selectedDate,
                                stateFor = { state.monthState[it] },
                                onClick = onSelectDate,
                                accent = accent
                            )
                        }
                    }
                }

                item {
                    if (state.selectedDate != null && state.selectedItems.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        XijiCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "${state.selectedDate.monthValue} 月 ${state.selectedDate.dayOfMonth} 日",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.primaryText
                                )
                                Spacer(Modifier.height(8.dp))
                                state.selectedItems.forEach { it ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(6.dp).clip(CircleShape).background(accent)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            when (it.state) {
                                                DayState.DONE -> "已完成"
                                                DayState.FAILED -> "未达成"
                                                DayState.SKIPPED -> "跳过"
                                                DayState.PAUSED -> "暂停"
                                                else -> "未记录"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.primaryText
                                        )
                                        Spacer(Modifier.weight(1f))
                                        TextButton(onClick = { editItem = it }) { Text("修改") }
                                    }
                                    it.record?.note?.takeIf { n -> n.isNotBlank() }?.let { n ->
                                        Text(
                                            "备注：$n",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.secondaryText,
                                            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    XijiCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("近一年", style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                                Spacer(Modifier.weight(1f))
                                HeatmapLegend()
                            }
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.horizontalScroll(rememberScrollState())) {
                                HabitHeatmap(state.heatmap, accent = accent)
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    XijiCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("最近 30 天趋势", style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                            Spacer(Modifier.height(12.dp))
                            CompletionTrendChart(state.trend, accent = accent)
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    XijiCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("历史记录", style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                            Spacer(Modifier.height(8.dp))
                            if (state.history.isEmpty()) {
                                Text(
                                    "还没有记录。今天开始也不晚。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                            } else {
                                state.history.take(30).forEach { r ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when (r.status) {
                                                        RecordStatus.DONE -> accent
                                                        RecordStatus.FAILED -> colors.danger
                                                        RecordStatus.SKIPPED -> colors.secondaryText
                                                    }
                                                )
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            "${r.date.monthValue}月${r.date.dayOfMonth}日",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.primaryText
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            when (r.status) {
                                                RecordStatus.DONE -> if (habit.isQuantity && r.accumulatedValue > 0)
                                                    "完成 · ${formatValue(r.accumulatedValue)}${habit.targetUnit}" else "完成"
                                                RecordStatus.FAILED -> "未达成"
                                                RecordStatus.SKIPPED -> "跳过"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.secondaryText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                snackbarHostState,
                Modifier.align(Alignment.BottomCenter).padding(16.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = colors.primaryText,
                    contentColor = colors.cardBackground,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }
    }

    editItem?.let { item ->
        val date = state.selectedDate ?: LocalDate.now()
        DayEditSheet(
            item = item,
            date = date,
            today = LocalDate.now(),
            onDismiss = { editItem = null },
            onSetStatus = { st -> onSetStatus(date, st) },
            onClear = { onClear(date) },
            onAddValue = { v -> onAddValue(date, v) },
            onUndoValue = { onUndoValue(date) },
            onSetNote = { n -> onSetNote(date, n) }
        )
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("删除这个习惯？") },
            text = { Text("将同时删除它的全部打卡记录，且无法恢复。如果只是想暂时停用，可以选择归档或暂停。") },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text("删除", color = colors.danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = onCancelDelete) { Text("取消") } },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val colors = LocalXijiColors.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                color = colors.primaryText
            )
            Spacer(Modifier.width(2.dp))
            Text(unit, style = MaterialTheme.typography.labelSmall, color = colors.secondaryText, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

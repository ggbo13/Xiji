package com.xiji.habits.ui.statistics

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiji.habits.domain.model.formatValue
import com.xiji.habits.ui.components.CompletionTrendChart
import com.xiji.habits.ui.components.EmptyState
import com.xiji.habits.ui.components.HabitHeatmap
import com.xiji.habits.ui.components.HabitIconBadge
import com.xiji.habits.ui.components.HairlineDivider
import com.xiji.habits.ui.components.HeatmapLegend
import com.xiji.habits.ui.components.SmoothProgressBar
import com.xiji.habits.ui.components.WeekBarChart
import com.xiji.habits.ui.components.XijiCard
import com.xiji.habits.ui.theme.LocalXijiColors
import java.time.LocalDate

@Composable
fun StatsScreen(
    state: StatsUiState,
    onSelectHabit: (Long?) -> Unit,
    onSetYear: (Int) -> Unit,
    onSelectDay: (LocalDate?) -> Unit,
    onOpenHabit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalXijiColors.current
    Box(modifier.fillMaxSize().background(colors.pageBackground)) {
        if (!state.hasData) {
            EmptyState(
                title = "还没有可统计的数据",
                description = "创建习惯并开始打卡后，这里会出现你的趋势、热力图和强度。",
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                    Text("统计", style = MaterialTheme.typography.headlineMedium, color = colors.primaryText)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "看清长期趋势，比追求完美更重要。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                XijiCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Row {
                            OverviewCell("累计完成", "${state.overview.totalCompletions}", "次", Modifier.weight(1f))
                            OverviewCell("有效天数", "${state.overview.totalDays}", "天", Modifier.weight(1f))
                            OverviewCell(
                                "最长连续",
                                "${state.overview.currentBestStreak}",
                                state.overview.currentBestUnit,
                                Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        HairlineDivider()
                        Spacer(Modifier.height(16.dp))
                        RateRow("本周完成率", state.overview.weekRate)
                        Spacer(Modifier.height(12.dp))
                        RateRow("本月完成率", state.overview.monthRate)
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                XijiCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("最近 7 天", style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "完成 ${state.weekTrend.sumOf { it.done }} 项",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        WeekBarChart(state.weekTrend)
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                XijiCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("最近 30 天", style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${(state.monthTrend.sumOf { it.done }.toFloat() / state.monthTrend.sumOf { it.scheduled }.coerceAtLeast(1) * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        CompletionTrendChart(state.monthTrend)
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                XijiCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("年度热力图", style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                            Spacer(Modifier.weight(1f))
                            YearChip(state.heatmapYear, onSetYear)
                        }
                        Spacer(Modifier.height(10.dp))
                        HabitScopeChips(state, onSelectHabit)
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.horizontalScroll(rememberScrollState())) {
                            HabitHeatmap(
                                aggregates = state.heatmap,
                                onDayClick = { onSelectDay(it) }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        HeatmapLegend()
                        if (state.selectedDay != null) {
                            Spacer(Modifier.height(10.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.subtleFill)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    "${state.selectedDay.monthValue} 月 ${state.selectedDay.dayOfMonth} 日 · 完成 ${state.selectedDayDone} / ${state.selectedDayScheduled}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primaryText
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                Text("各习惯完成率", style = MaterialTheme.typography.titleSmall, color = colors.secondaryText)
                Spacer(Modifier.height(10.dp))
            }

            items(state.habitStats, key = { it.habit.id }, contentType = { "habit-stat" }) { row ->
                XijiCard(Modifier.fillMaxWidth(), onClick = { onOpenHabit(row.habit.id) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        HabitIconBadge(row.habit.icon, row.habit.color, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    row.habit.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.primaryText,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${(row.stats.overallRate * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                    color = colors.secondaryText
                                )
                            }
                            Spacer(Modifier.height(7.dp))
                            SmoothProgressBar(
                                progress = row.stats.overallRate,
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(row.habit.color),
                                height = 5.dp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "强度 ${row.stats.strength} · 连续 ${row.stats.currentStreak} ${row.stats.streakUnit} · 累计 ${row.stats.totalCompletions} 次",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "统计口径：分母只包含有效计划日；暂停与跳过不计入；今天尚未结束不计入历史完成率；未来日期不参与统计。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText
                )
            }
        }
    }
}

@Composable
private fun OverviewCell(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val colors = LocalXijiColors.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
        Spacer(Modifier.height(6.dp))
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

@Composable
private fun RateRow(label: String, rate: Float) {
    val colors = LocalXijiColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText, modifier = Modifier.width(88.dp))
        SmoothProgressBar(progress = rate, modifier = Modifier.weight(1f), height = 6.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            "${(rate * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = colors.primaryText,
            modifier = Modifier.width(38.dp)
        )
    }
}

@Composable
private fun YearChip(year: Int, onSetYear: (Int) -> Unit) {
    val colors = LocalXijiColors.current
    val current = LocalDate.now().year
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(current - 1, current).forEach { y ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (y == year) colors.primaryText else colors.subtleFill)
                    .clickable { onSetYear(y) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "$y",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = if (y == year) colors.cardBackground else colors.secondaryText
                )
            }
        }
    }
}

@Composable
private fun HabitScopeChips(state: StatsUiState, onSelect: (Long?) -> Unit) {
    val colors = LocalXijiColors.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ScopeChip("全部习惯", state.selectedHabitId == null) { onSelect(null) }
        state.habitStats.forEach { row ->
            ScopeChip(row.habit.name, state.selectedHabitId == row.habit.id) { onSelect(row.habit.id) }
        }
    }
}

@Composable
private fun ScopeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalXijiColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else colors.subtleFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else colors.secondaryText,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

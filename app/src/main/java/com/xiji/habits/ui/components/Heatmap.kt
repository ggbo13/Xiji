package com.xiji.habits.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xiji.habits.domain.statistics.StatisticsEngine
import com.xiji.habits.ui.theme.LocalXijiColors
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * GitHub 风格热力图。
 * - 5 个强度等级：无计划 / 未完成 / 低 / 中 / 高（全完成）
 * - 区分「没有计划任务」与「计划了但未达标」
 * - 只使用 Compose Canvas/Box 绘制，不依赖网络资源
 */
@Composable
fun HabitHeatmap(
    aggregates: List<StatisticsEngine.DayAggregate>,
    modifier: Modifier = Modifier,
    cellSize: androidx.compose.ui.unit.Dp = 11.dp,
    gap: androidx.compose.ui.unit.Dp = 3.dp,
    accent: Color = MaterialTheme.colorScheme.primary,
    onDayClick: ((LocalDate) -> Unit)? = null
) {
    val colors = LocalXijiColors.current
    if (aggregates.isEmpty()) return
    val start = aggregates.first().date
    val end = aggregates.last().date
    // 从 start 所在周的周一开始
    val firstMonday = start.minusDays((start.dayOfWeek.value - 1).toLong())
    val totalDays = (end.toEpochDay() - firstMonday.toEpochDay()).toInt() + 1
    val weeks = (totalDays + 6) / 7
    val byDate = aggregates.associateBy { it.date }

    Row(modifier) {
        Column(Modifier.padding(top = 18.dp, end = 6.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            listOf("一", "", "三", "", "五", "", "日").forEach { label ->
                Box(Modifier.height(cellSize), contentAlignment = Alignment.CenterStart) {
                    if (label.isNotEmpty()) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
                    }
                }
            }
        }
        Column {
            // 月份标签：只在距离上一个标签足够远时绘制，避免挤压
            Row(Modifier.height(16.dp)) {
                var lastMonth = -1
                var lastLabelWeek = -10
                repeat(weeks) { w ->
                    val d = firstMonday.plusDays((w * 7).toLong())
                    val canLabel = d.monthValue != lastMonth && w - lastLabelWeek >= 4
                    if (canLabel) {
                        lastMonth = d.monthValue
                        lastLabelWeek = w
                        Text(
                            "${d.monthValue}月",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText,
                            maxLines = 1,
                            modifier = Modifier.width(cellSize + gap + 14.dp)
                        )
                    } else {
                        if (d.monthValue != lastMonth) lastMonth = d.monthValue
                        Spacer(Modifier.width(cellSize + gap))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(weeks) { w ->
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(7) { dayIdx ->
                            val date = firstMonday.plusDays((w * 7L + dayIdx))
                            val agg = byDate[date]
                            val color = when {
                                agg == null -> Color.Transparent
                                agg.scheduled == 0 -> colors.subtleFill.copy(alpha = 0.45f)
                                agg.done == 0 -> colors.subtleFill
                                agg.done >= agg.scheduled -> accent
                                agg.done.toFloat() / agg.scheduled >= 0.5f -> accent.copy(alpha = 0.62f)
                                else -> accent.copy(alpha = 0.34f)
                            }
                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .clip(RoundedCornerShape(2.5.dp))
                                    .background(color)
                                    .then(
                                        if (onDayClick != null && agg != null) Modifier
                                            .semantics {
                                                contentDescription =
                                                    "${date.monthValue}月${date.dayOfMonth}日，完成 ${agg.done}/${agg.scheduled}"
                                            }
                                        else Modifier
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeatmapLegend(modifier: Modifier = Modifier) {
    val colors = LocalXijiColors.current
    val accent = MaterialTheme.colorScheme.primary
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("少", style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
        Spacer(Modifier.width(6.dp))
        listOf(
            colors.subtleFill,
            accent.copy(alpha = 0.34f),
            accent.copy(alpha = 0.62f),
            accent
        ).forEach {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(it))
            Spacer(Modifier.width(3.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text("多", style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(colors.subtleFill.copy(alpha = 0.45f)))
        Spacer(Modifier.width(4.dp))
        Text("无计划", style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
    }
}

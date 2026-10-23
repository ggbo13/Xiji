package com.xiji.habits.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.xiji.habits.domain.statistics.StatisticsEngine
import com.xiji.habits.ui.theme.LocalXijiColors
import java.time.format.DateTimeFormatter

/**
 * 轻量折线图：纯 Canvas 绘制，支持点按查看数值，适配深色模式。
 */
@Composable
fun CompletionTrendChart(
    points: List<StatisticsEngine.DayAggregate>,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    height: androidx.compose.ui.unit.Dp = 120.dp
) {
    val colors = LocalXijiColors.current
    if (points.isEmpty()) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
        }
        return
    }
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val values = points.map { if (it.scheduled == 0) 0f else it.done.toFloat() / it.scheduled }
    val labelColor = colors.secondaryText

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val step = size.width.toFloat() / (points.size - 1).coerceAtLeast(1)
                        val idx = (offset.x / step).toInt().coerceIn(0, points.lastIndex)
                        selectedIndex = if (selectedIndex == idx) null else idx
                    }
                }
        ) {
            Canvas(Modifier.fillMaxWidth().height(height)) {
                val w = size.width
                val h = size.height
                val padTop = 8.dp.toPx()
                val padBottom = 8.dp.toPx()
                val usableH = h - padTop - padBottom
                val step = if (points.size > 1) w / (points.size - 1) else w

                // 网格
                for (i in 0..2) {
                    val y = padTop + usableH * (i / 2f)
                    drawLine(
                        color = colors.divider,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                }

                val path = Path()
                val fillPath = Path()
                values.forEachIndexed { i, v ->
                    val x = i * step
                    val y = padTop + usableH * (1f - v)
                    if (i == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, padTop + usableH)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo((values.size - 1) * step, padTop + usableH)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.02f))
                    )
                )
                drawPath(
                    path = path,
                    color = accent,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                )

                selectedIndex?.let { idx ->
                    val x = idx * step
                    val y = padTop + usableH * (1f - values[idx])
                    drawLine(accent.copy(alpha = 0.5f), Offset(x, padTop), Offset(x, padTop + usableH), 1.5f)
                    drawCircle(colors.cardBackground, 6.dp.toPx(), Offset(x, y))
                    drawCircle(accent, 4.dp.toPx(), Offset(x, y))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val fmt = DateTimeFormatter.ofPattern("M/d")
        Row(Modifier.fillMaxWidth()) {
            val idx = selectedIndex
            if (idx != null && idx in points.indices) {
                val p = points[idx]
                Text(
                    "${p.date.format(fmt)} · 完成 ${p.done}/${p.scheduled}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primaryText
                )
            } else {
                Text(
                    points.first().date.format(fmt),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor
                )
                Spacer(Modifier.weight(1f))
                Text(
                    points.last().date.format(fmt),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor
                )
            }
        }
    }
}

/** 迷你柱状图，用于最近七天 */
@Composable
fun WeekBarChart(
    points: List<StatisticsEngine.DayAggregate>,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val colors = LocalXijiColors.current
    Row(
        modifier.fillMaxWidth().height(90.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        points.forEach { p ->
            val ratio = if (p.scheduled == 0) 0f else p.done.toFloat() / p.scheduled
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((64 * ratio.coerceAtLeast(if (p.scheduled > 0) 0.06f else 0f)).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (ratio >= 1f) accent else accent.copy(alpha = 0.45f))
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${p.date.dayOfMonth}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText
                )
            }
        }
    }
}

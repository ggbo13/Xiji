package com.xiji.habits.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.xiji.habits.ui.theme.LocalXijiColors
import java.time.LocalDate
import java.time.YearMonth

/**
 * 月历网格。使用符号 + 颜色双重编码，保证不仅依赖颜色区分状态：
 * - 实心圆：已完成
 * - 半环 / 小点：部分完成
 * - 空心圆：计划但未完成
 * - 短横线：跳过
 * - 灰点：暂停
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    stateFor: (LocalDate) -> DayState?,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val colors = LocalXijiColors.current
    val firstDay = month.atDay(1)
    val leading = (firstDay.dayOfWeek.value - 1)
    val daysInMonth = month.lengthOfMonth()
    val totalCells = leading + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - leading + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = month.atDay(dayNum)
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date == selected,
                            state = stateFor(date),
                            accent = accent,
                            onClick = { onClick(date) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    state: DayState?,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalXijiColors.current
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .then(
                when {
                    isSelected -> Modifier.background(MaterialTheme.colorScheme.primary)
                    isToday -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else -> Modifier
                }
            )
            .clickable(enabled = !date.isAfter(LocalDate.now())) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        val fg = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isToday -> MaterialTheme.colorScheme.primary
            date.isAfter(LocalDate.now()) -> colors.secondaryText.copy(alpha = 0.35f)
            else -> colors.primaryText
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = fg,
                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            Spacer(Modifier.height(2.dp))
            val marker = if (isSelected) MaterialTheme.colorScheme.onPrimary else accent
            when (state) {
                DayState.DONE -> Box(Modifier.size(5.dp).clip(CircleShape).background(marker))
                DayState.PARTIAL -> Box(
                    Modifier.size(5.dp).clip(CircleShape)
                        .background(marker.copy(alpha = 0.45f))
                )
                DayState.FAILED -> Box(Modifier.size(5.dp).clip(CircleShape).background(colors.danger))
                DayState.PENDING -> Box(
                    Modifier.size(5.dp).clip(CircleShape)
                        .border(1.dp, colors.cardBorder, CircleShape)
                )
                DayState.SKIPPED -> Box(Modifier.size(5.dp, 1.5.dp).background(colors.secondaryText))
                DayState.PAUSED -> Box(Modifier.size(4.dp).clip(CircleShape).background(colors.cardBorder))
                else -> Spacer(Modifier.height(5.dp))
            }
        }
    }
}

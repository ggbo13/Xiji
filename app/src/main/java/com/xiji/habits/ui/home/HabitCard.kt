package com.xiji.habits.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.domain.model.formatValue
import com.xiji.habits.ui.components.CheckmarkButton
import com.xiji.habits.ui.components.HabitIconBadge
import com.xiji.habits.ui.components.PillLabel
import com.xiji.habits.ui.components.SmoothProgressBar
import com.xiji.habits.ui.components.XijiCard
import com.xiji.habits.ui.theme.LocalReduceMotion
import com.xiji.habits.ui.theme.LocalXijiColors
import com.xiji.habits.ui.theme.XijiMotion

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitCard(
    row: HabitRowUi,
    isToday: Boolean,
    onToggle: (Habit) -> Unit,
    onOpenDetail: (Long) -> Unit,
    onLongPress: (Habit) -> Unit,
    modifier: Modifier = Modifier,
    onQuickAdd: ((Habit) -> Unit)? = null,
    reorderMode: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    dragActions: List<CustomAccessibilityAction> = emptyList()
) {
    val colors = LocalXijiColors.current
    val reduceMotion = LocalReduceMotion.current
    val habit = row.habit
    val accent = Color(habit.color)
    val done = row.state == DayState.DONE
    val disabled = row.state == DayState.PAUSED || row.state == DayState.FUTURE

    XijiCard(
        modifier = modifier.fillMaxWidth(),
        // 排序时整卡不再响应点击：避免拖拽把手的按下被当成「打开详情」
        onClick = if (reorderMode) null else ({ onOpenDetail(habit.id) })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = !reorderMode,
                    onClick = { onOpenDetail(habit.id) },
                    onLongClick = { onLongPress(habit) }
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HabitIconBadge(icon = habit.icon, color = habit.color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (disabled) colors.secondaryText else colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (row.isPaused) {
                        Spacer(Modifier.width(6.dp))
                        PillLabel("暂停", color = colors.secondaryText, container = colors.subtleFill)
                    }
                    if (row.record?.isBackfilled == true) {
                        Spacer(Modifier.width(6.dp))
                        PillLabel("补签", color = colors.warning, container = colors.subtleFill)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (habit.isQuantity && !done) {
                    Spacer(Modifier.height(8.dp))
                    val current = row.record?.accumulatedValue ?: 0.0
                    val target = if (row.targetValue > 0) row.targetValue else 1.0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmoothProgressBar(
                            progress = (current / target).toFloat(),
                            modifier = Modifier.weight(1f),
                            color = accent,
                            height = 5.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${formatValue(current)}/${formatValue(target)}${habit.targetUnit}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = colors.secondaryText
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // 排序模式与打卡按钮之间做一次淡入淡出，切换时卡片不会突然「跳」一下
            Crossfade(
                targetState = reorderMode,
                animationSpec = if (reduceMotion) snap() else tween(XijiMotion.FAST),
                label = "trailing"
            ) { sorting ->
                if (sorting) {
                    DragHandle(
                        modifier = dragHandleModifier,
                        contentDescription = "拖动 ${habit.name} 调整顺序",
                        actions = dragActions
                    )
                } else when {
                    habit.isQuantity && !done -> {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = if (colors.isDark) 0.25f else 0.12f))
                                .combinedClickable(
                                    onClick = { onQuickAdd?.invoke(habit) ?: onToggle(habit) },
                                    onLongClick = { onOpenDetail(habit.id) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "记录一次 ${habit.name}",
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    else -> CheckmarkButton(
                        checked = done,
                        onToggle = { onToggle(habit) },
                        accent = accent,
                        enabled = !disabled,
                        contentDescription = when {
                            done -> "${habit.name} 已完成，点击撤销"
                            !isToday -> "补签 ${habit.name}"
                            else -> "完成 ${habit.name}"
                        }
                    )
                }
            }
        }
    }
}

/** 排序模式下的拖拽把手，44dp 的触控区域方便单手握持 */
@Composable
private fun DragHandle(
    modifier: Modifier,
    contentDescription: String,
    actions: List<CustomAccessibilityAction>
) {
    val colors = LocalXijiColors.current
    Box(
        modifier = Modifier
            .size(DragHandleSize)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.subtleFill)
            .then(modifier)
            // 独立成一个语义节点：否则会被整张卡片的 combinedClickable 合并掉，
            // TalkBack 找不到它，UI 测试也点不准
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                if (actions.isNotEmpty()) customActions = actions
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.DragIndicator,
            contentDescription = null,
            tint = colors.secondaryText,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun subtitle(row: HabitRowUi): String {
    val habit = row.habit
    val parts = mutableListOf<String>()
    when {
        row.state == DayState.PAUSED -> parts.add("暂停中")
        habit.isQuantity && row.weekTarget > 0 && habit.schedule.type.name == "TIMES_PER_WEEK" ->
            parts.add("本周 ${row.weekDone} / ${row.weekTarget} 次")
        else -> parts.add(habit.schedule.describe())
    }
    if (row.streakCount > 0) {
        parts.add("坚持 ${row.streakCount} ${row.streakUnit}")
    } else {
        parts.add("从今天开始")
    }
    if (habit.isQuantity && habit.schedule.type.name != "TIMES_PER_WEEK") {
        parts.add("${formatValue(row.targetValue)}${habit.targetUnit}")
    }
    return parts.joinToString(" · ")
}

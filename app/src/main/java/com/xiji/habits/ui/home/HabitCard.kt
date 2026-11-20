package com.xiji.habits.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
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
    onQuickAdd: ((Habit, Double) -> Unit)? = null,
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
                        val current = row.record?.accumulatedValue ?: 0.0
                        val target = if (row.targetValue > 0) row.targetValue else 1.0
                        QuantityAddButton(
                            habitName = habit.name,
                            accent = accent,
                            reduceMotion = reduceMotion,
                            onAddOne = { onQuickAdd?.invoke(habit, 1.0) ?: onToggle(habit) },
                            onFill = {
                                val remaining = (target - current).coerceAtLeast(1.0)
                                onQuickAdd?.invoke(habit, remaining) ?: onToggle(habit)
                            }
                        )
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

/** 计次快捷按钮：轻点加一，长按进度环走满后补足当天目标。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuantityAddButton(
    habitName: String,
    accent: Color,
    reduceMotion: Boolean,
    onAddOne: () -> Unit,
    onFill: () -> Unit
) {
    val colors = LocalXijiColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(
            durationMillis = if (pressed) 500 else 120,
            easing = LinearEasing
        ),
        label = "quantityAddProgress"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.94f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "quantityAddScale"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(accent.copy(alpha = if (colors.isDark) 0.25f else 0.12f))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = "加 1",
                onLongClickLabel = "补满",
                onLongClick = onFill,
                onClick = onAddOne
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "点按加 1 $habitName，长按补满"
            },
        contentAlignment = Alignment.Center
    ) {
        if (progress > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val strokeWidth = 2.dp.toPx()
                val inset = strokeWidth / 2f
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        width = size.width - strokeWidth,
                        height = size.height - strokeWidth
                    ),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text = "+1",
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = accent,
            fontWeight = FontWeight.SemiBold
        )
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

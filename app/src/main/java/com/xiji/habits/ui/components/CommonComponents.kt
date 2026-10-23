package com.xiji.habits.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiji.habits.ui.theme.LocalReduceMotion
import com.xiji.habits.ui.theme.LocalXijiColors

val CardShape = RoundedCornerShape(18.dp)
val FieldShape = RoundedCornerShape(14.dp)
val PillShape = RoundedCornerShape(50)

@Composable
fun XijiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = LocalXijiColors.current.cardBackground,
    border: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val colors = LocalXijiColors.current
    val reduceMotion = LocalReduceMotion.current
    // 按下时整卡轻微内收，松手回弹：手指下面的东西「有反应」比高亮更直接
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.99f else 1f,
        animationSpec = if (reduceMotion) snap() else
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardPress"
    )
    val base = modifier
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
        .clip(CardShape)
        .then(
            if (onClick != null) Modifier.clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            ) else Modifier
        )
        .background(container, CardShape)
    Surface(
        modifier = base,
        shape = CardShape,
        color = Color.Transparent,
        border = if (border) BorderStroke(1.dp, colors.cardBorder) else null
    ) {
        Column(content = content)
    }
}

/** 进度条：平滑增长，深色模式自动适配 */
@Composable
fun SmoothProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = LocalXijiColors.current.subtleFill,
    height: androidx.compose.ui.unit.Dp = 8.dp,
    contentDescription: String? = null
) {
    val reduceMotion = LocalReduceMotion.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 520),
        label = "progress"
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(PillShape)
            .background(track)
            .then(
                if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription }
                else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(PillShape)
                .drawBehind {
                    // 动画只更新绘制，不在滚动过程中逐帧重组、重新测量布局。
                    val barWidth = size.width * animated
                    val radius = minOf(barWidth, size.height) / 2f
                    drawRoundRect(color, size = Size(barWidth, size.height), cornerRadius = CornerRadius(radius, radius))
                }
        )
    }
}

/** 圆形进度环 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 76.dp,
    stroke: androidx.compose.ui.unit.Dp = 7.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = LocalXijiColors.current.subtleFill,
    label: String? = null
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(560), label = "ring")
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = LocalXijiColors.current.primaryText
            )
        }
    }
}

/** 章节标题 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = LocalXijiColors.current.secondaryText
        )
        action?.invoke()
    }
}

/** 空状态 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    val colors = LocalXijiColors.current
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.subtleFill),
            contentAlignment = Alignment.Center
        ) {
            Text("◌", fontSize = 26.sp, color = colors.secondaryText)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.primaryText)
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryText,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

/** 标签胶囊 */
@Composable
fun PillLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    alpha: Float = 1f
) {
    Box(
        modifier = modifier
            .alpha(alpha)
            .clip(PillShape)
            .background(container)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** 带图标背景的方块 */
@Composable
fun HabitIconBadge(
    icon: String,
    color: Long,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp
) {
    val c = Color(color)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(c.copy(alpha = if (LocalXijiColors.current.isDark) 0.22f else 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = HabitIcons.emojiFor(icon), fontSize = (size.value * 0.45f).sp)
    }
}

/** 分隔线 */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalXijiColors.current.divider))
}

/** 数字使用等宽字形，避免跳动 */
@Composable
fun StatNumber(text: String, modifier: Modifier = Modifier, color: Color = LocalXijiColors.current.primaryText) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
        color = color
    )
}

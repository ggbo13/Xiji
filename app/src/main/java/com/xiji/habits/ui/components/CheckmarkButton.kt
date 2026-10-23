package com.xiji.habits.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiji.habits.ui.theme.LocalReduceMotion
import com.xiji.habits.ui.theme.LocalXijiColors
import com.xiji.habits.ui.theme.XijiMotion

/**
 * 打卡按钮：按下有轻微缩放，勾选图标平滑出现。
 * 触控区域保持 48dp 以上，并带有 TalkBack 描述。
 */
@Composable
fun CheckmarkButton(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalXijiColors.current.let { androidx.compose.material3.MaterialTheme.colorScheme.primary },
    size: Dp = 40.dp,
    enabled: Boolean = true,
    contentDescription: String = if (checked) "已完成，点击撤销" else "点击完成"
) {
    val colors = LocalXijiColors.current
    val reduceMotion = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = if (reduceMotion) snap() else
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "checkScale"
    )
    // 底色和描边一起过渡，勾选时不会出现「先变色再出现对勾」的两段式
    val fillSpec = if (reduceMotion) snap<Color>() else tween<Color>(XijiMotion.FAST)
    val fill by animateColorAsState(
        targetValue = if (checked) accent else Color.Transparent,
        animationSpec = fillSpec,
        label = "checkFill"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) accent else colors.cardBorder,
        animationSpec = fillSpec,
        label = "checkBorder"
    )
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .toggleable(
                value = checked,
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox
            ) {
                if (enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                }
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(fill)
                .border(width = 1.5.dp, color = borderColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = checked,
                enter = if (reduceMotion) fadeIn(snap()) else
                    scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                exit = if (reduceMotion) fadeOut(snap()) else scaleOut()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    }
}

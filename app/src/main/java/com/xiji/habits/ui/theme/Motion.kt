package com.xiji.habits.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset

/**
 * 动效总开关。设置页打开「减少动画」后，所有位移/淡入淡出降级为直接切换，
 * 拖拽本身仍然跟手（那是用户直接操控，不属于动画）。
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * 全局动效语汇：克制的弹性 + 短时长。
 * 统一放在这里，避免每个界面各写一套 spring 参数导致手感不一致。
 */
object XijiMotion {

    /** 按压、勾选这类 1 秒内发生上百次的小反馈 */
    const val FAST = 140

    /** 常规的展开、切换 */
    const val MEDIUM = 220

    /** 拖拽松手后归位的弹簧：快、几乎不回弹，避免「弹一下」的廉价感 */
    fun settle(reduce: Boolean): FiniteAnimationSpec<Float> =
        if (reduce) snap() else spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMedium)

    /** 被拖起的卡片放大 / 阴影的过渡 */
    fun lift(reduce: Boolean): FiniteAnimationSpec<Float> =
        if (reduce) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** 列表项换位（Compose 的 animateItem placement） */
    fun placement(reduce: Boolean): FiniteAnimationSpec<IntOffset> =
        if (reduce) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = IntOffset.VisibilityThreshold
        )

    /** 列表项出现 / 消失 */
    fun fade(reduce: Boolean): FiniteAnimationSpec<Float> =
        if (reduce) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = 0.01f
        )
}

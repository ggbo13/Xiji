package com.xiji.habits.widget

import android.content.Context
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager

/**
 * 小组件的真实尺寸。
 *
 * 为什么不用 `LocalSize`：在多数启动器上 Glance 回报的是 provider 里声明的
 * minHeight（110dp），而不是组件被拉伸后的实际高度。用它算行数会导致
 * 4×2 只画 1 行、2×2 一行都不画，下方留下大片空白（真机截图已复现）。
 *
 * 这里改为向 `AppWidgetManager` 查询实际 cell 尺寸，并用一个保守的下限兜底。
 */
object WidgetSize {

    /** 单个 grid cell 的估算高度（多数启动器 70–80dp，取保守值） */
    private const val CELL_DP = 74f
    /** 上下 padding 与圆角留白 */
    private const val VERTICAL_PADDING = 24f

    suspend fun resolve(context: Context, glanceId: GlanceId, fallback: DpSize): DpSize {
        val fromManager = runCatching {
            GlanceAppWidgetManager(context).getAppWidgetSizes(glanceId)
        }.getOrNull()
        val best = fromManager?.maxByOrNull { it.height.value * it.width.value }
        if (best != null && best.height > 40.dp && best.width > 40.dp) return best
        return fallback
    }

    /**
     * 依据可用高度推算能显示多少行。
     *
     * @param height 组件总高度
     * @param headerHeight 头部（标题/进度）占用高度
     * @param rowHeight 单行高度
     */
    fun rowCount(height: Dp, headerHeight: Dp, rowHeight: Dp): Int {
        val available = height - headerHeight - VERTICAL_PADDING.dp
        if (available <= 0.dp) return 0
        return (available.value / rowHeight.value).toInt().coerceAtLeast(0)
    }

    /** provider 未声明 targetCellHeight 时的兜底估算 */
    fun estimateFromCells(cellHeight: Int): Dp = (cellHeight * CELL_DP).dp
}

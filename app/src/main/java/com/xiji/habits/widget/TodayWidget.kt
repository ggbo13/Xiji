package com.xiji.habits.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.xiji.habits.MainActivity
import com.xiji.habits.domain.model.HabitType

/**
 * 4×2 小组件：今日习惯列表，支持三种习惯类型各自的操作。
 *
 * - 完成型：点右侧「完成」→ 勾选；再点勾选圆 → 撤销
 * - 数量型：点「+1」累加；达到目标自动完成
 * - 戒除型：点「守住」记为达成；点「重来」撤销破戒
 * - 点名称进入 App 详情（数量型需要精确输入时用）
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetData.load(context, maxItems = 12)
        val palette = WidgetPalette.of(context)
        val size = WidgetSize.resolve(context, id, DpSize(320.dp, 150.dp))
        provideContent { TodayContent(snapshot, palette, size) }
    }
}

@Composable
private fun TodayContent(snapshot: WidgetSnapshot, palette: WidgetPalette, size: DpSize) {
    val rowHeight = 38.dp
    val header = 26.dp
    val maxRows = WidgetSize.rowCount(size.height, header, rowHeight).coerceIn(1, 8)
    val percent = (snapshot.progress * 100).toInt()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .cornerRadius(22.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(header)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("今日", style = textStyle(13, palette.text, bold = true))
            Spacer(GlanceModifier.width(8.dp))
            Text(
                "${snapshot.completed}/${snapshot.total}",
                style = textStyle(12, palette.secondary)
            )
            Spacer(GlanceModifier.width(10.dp))
            Box(
                GlanceModifier
                    .defaultWeight()
                    .height(4.dp)
                    .cornerRadius(2.dp)
                    .background(palette.track)
            ) {
                val barWidth = ((size.width - 150.dp) * snapshot.progress.coerceIn(0f, 1f))
                    .coerceAtLeast(0.dp)
                if (barWidth > 0.dp) {
                    Box(
                        GlanceModifier
                            .width(barWidth)
                            .height(4.dp)
                            .cornerRadius(2.dp)
                            .background(palette.primary)
                    ) {}
                }
            }
            Spacer(GlanceModifier.width(10.dp))
            Text("$percent%", style = textStyle(11, palette.primary, bold = true))
        }

        if (snapshot.habits.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentAlignment = Alignment.Center
            ) {
                Text("今天没有安排的习惯", style = textStyle(12, palette.secondary))
            }
        } else {
            snapshot.habits.take(maxRows).forEach { h ->
                HabitRow(h, palette, rowHeight)
            }
        }
    }
}

@Composable
private fun HabitRow(h: WidgetHabit, palette: WidgetPalette, rowHeight: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(rowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧状态圆：点击可撤销完成（完成型/戒除型）
        Box(
            modifier = GlanceModifier
                .size(24.dp)
                .cornerRadius(12.dp)
                .background(if (h.done) palette.primary else palette.track)
                .clickable(
                    actionRunCallback<WidgetActionCallback>(
                        actionParametersOf(
                            WidgetKeys.HABIT_ID to h.id,
                            WidgetKeys.ACTION to WidgetAction.TOGGLE_COMPLETE.key
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                h.done -> Text("✓", style = textStyle(12, palette.onPrimary, bold = true))
                h.failed -> Text("✕", style = textStyle(11, palette.danger, bold = true))
                else -> Box(
                    GlanceModifier.size(16.dp).cornerRadius(8.dp).background(palette.background)
                ) {}
            }
        }
        Spacer(GlanceModifier.width(9.dp))

        // 名称 + 副标题（数量进度 / 类型说明），点击进入 App 详情
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(
                    actionRunCallback<WidgetActionCallback>(
                        actionParametersOf(
                            WidgetKeys.HABIT_ID to h.id,
                            WidgetKeys.ACTION to WidgetAction.OPEN_HABIT.key
                        )
                    )
                )
        ) {
            Text(
                h.name,
                maxLines = 1,
                style = textStyle(12, if (h.done) palette.secondary else palette.text)
            )
            val sub = h.quantityLabel
            if (sub != null && !h.done) {
                Text(sub, maxLines = 1, style = textStyle(10, palette.secondary))
            }
        }

        // 右侧操作：按类型给出不同文案
        val label = h.actionLabel
        if (label != null) {
            val action = when (h.type) {
                HabitType.QUANTITY -> WidgetAction.ADD_ONE
                HabitType.QUIT -> if (h.failed) WidgetAction.QUIT_FAIL else WidgetAction.QUIT_HOLD
                HabitType.COMPLETE -> WidgetAction.TOGGLE_COMPLETE
            }
            Box(
                modifier = GlanceModifier
                    .cornerRadius(9.dp)
                    .background(palette.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .clickable(
                        actionRunCallback<WidgetActionCallback>(
                            actionParametersOf(
                                WidgetKeys.HABIT_ID to h.id,
                                WidgetKeys.ACTION to action.key
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = textStyle(11, palette.primary, bold = true), maxLines = 1)
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

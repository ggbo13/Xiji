package com.xiji.habits.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
 * 2×2 小组件：今日完成进度。
 *
 * 信息层级：日期/百分比 → 大数字 → 进度条 → 待办前三项。
 * 行数依据组件真实高度计算（见 [WidgetSize]），不再依赖会失真的 LocalSize。
 */
class ProgressWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetData.load(context, maxItems = 6)
        val palette = WidgetPalette.of(context)
        val size = WidgetSize.resolve(context, id, DpSize(160.dp, 160.dp))
        provideContent { ProgressContent(snapshot, palette, size) }
    }
}

@Composable
private fun ProgressContent(snapshot: WidgetSnapshot, palette: WidgetPalette, size: DpSize) {
    val compact = size.height < 150.dp
    val header = if (compact) 48.dp else 54.dp
    val rowHeight = 20.dp
    val maxRows = WidgetSize.rowCount(size.height, header, rowHeight).coerceIn(0, 4)
    val percent = (snapshot.progress * 100).toInt()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .cornerRadius(22.dp)
            .padding(horizontal = 15.dp, vertical = 13.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${snapshot.date.monthValue} 月 ${snapshot.date.dayOfMonth} 日",
                style = textStyle(11, palette.secondary),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            if (snapshot.total > 0) {
                Text("$percent%", style = textStyle(11, palette.primary, bold = true))
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${snapshot.completed}",
                style = textStyle(if (compact) 28 else 32, palette.text, bold = true)
            )
            Text(
                " / ${snapshot.total}",
                style = textStyle(12, palette.secondary),
                modifier = GlanceModifier.padding(bottom = 4.dp)
            )
        }
        Spacer(GlanceModifier.height(8.dp))

        val usable = (size.width - 30.dp).coerceAtLeast(0.dp)
        val fill = usable * snapshot.progress.coerceIn(0f, 1f)
        Box(
            GlanceModifier
                .fillMaxWidth()
                .height(6.dp)
                .cornerRadius(3.dp)
                .background(palette.track)
        ) {
            if (fill > 0.dp) {
                Box(
                    GlanceModifier
                        .width(fill)
                        .height(6.dp)
                        .cornerRadius(3.dp)
                        .background(palette.primary)
                ) {}
            }
        }

        if (maxRows > 0) {
            Spacer(GlanceModifier.height(6.dp))
            if (snapshot.habits.isEmpty()) {
                Text("今天没有安排", style = textStyle(10, palette.secondary), maxLines = 1)
            } else {
                snapshot.habits.take(maxRows).forEach { h ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().height(rowHeight),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(if (h.done) 6.dp else 5.dp)
                                .cornerRadius(3.dp)
                                .background(
                                    when {
                                        h.done -> palette.primary
                                        h.failed -> palette.danger
                                        else -> palette.track
                                    }
                                )
                        ) {}
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            h.name,
                            maxLines = 1,
                            style = textStyle(10, if (h.done) palette.secondary else palette.text),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        // 数量型直接给 +1，不必打开 App
                        if (h.type == HabitType.QUANTITY && !h.done) {
                            Box(
                                modifier = GlanceModifier
                                    .cornerRadius(6.dp)
                                    .background(palette.primary.copy(alpha = 0.14f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                    .clickable(
                                        actionRunCallback<WidgetActionCallback>(
                                            actionParametersOf(
                                                WidgetKeys.HABIT_ID to h.id,
                                                WidgetKeys.ACTION to WidgetAction.ADD_ONE.key
                                            )
                                        )
                                    )
                            ) {
                                Text("+1", style = textStyle(9, palette.primary, bold = true))
                            }
                        }
                    }
                }
            }
        }
    }
}

class ProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProgressWidget()
}

package com.xiji.habits.widget

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * 小组件的视觉预览。
 *
 * Glance 在 Robolectric 下没有可用的 RemoteViews 宿主，无法直接截图，
 * 因此这里用 Compose 按 1:1 复刻两个小组件的布局参数（同样的 padding/字号/圆角/行高），
 * 输出 PNG 供肉眼检查排版。布局参数的事实来源仍是 widget 包下的两个组件。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetPreviewTest {

    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val palette = WidgetPalette(
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFF1F3EF),
        primary = Color(0xFF416B5B),
        text = Color(0xFF202824),
        secondary = Color(0xFF78817B),
        track = Color(0xFFE8ECE7),
        danger = Color(0xFFA85A54),
        onPrimary = Color(0xFFFFFFFF)
    )

    private val darkPalette = WidgetPalette(
        background = Color(0xFF202721),
        surface = Color(0xFF283029),
        primary = Color(0xFF92C6AA),
        text = Color(0xFFEBF0EA),
        secondary = Color(0xFF9AA69D),
        track = Color(0xFF2F3832),
        danger = Color(0xFFE5A19B),
        onPrimary = Color(0xFF10231A)
    )

    private data class HabitRowUi(
        val name: String,
        val done: Boolean,
        val qty: String? = null,
        val action: String? = null
    )

    // 与新版 WidgetHabit.actionLabel 一致：完成型=完成，数量型=+1
    private val rows = listOf(
        HabitRowUi("喝水", false, "1/8杯", "+1"),
        HabitRowUi("运动", false, null, "完成"),
        HabitRowUi("彬彬", true),
        HabitRowUi("阅读 30 分钟", true)
    )

    @Composable
    private fun ProgressPreview(p: WidgetPalette, dark: Boolean) {
        val completed = rows.count { it.done }
        val percent = completed * 100 / rows.size
        Column(
            Modifier
                .size(160.dp, 160.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(p.background)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("10 月 23 日", color = p.secondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("$percent%", color = p.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$completed", color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(" / ${rows.size}", color = p.secondary, fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 4.dp))
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.track)
            ) {
                Box(
                    Modifier.fillMaxWidth(completed.toFloat() / rows.size)
                        .height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.primary)
                )
            }
            Spacer(Modifier.height(8.dp))
            rows.take(2).forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(if (r.done) 7.dp else 6.dp).clip(CircleShape)
                        .background(if (r.done) p.primary else p.track))
                    Spacer(Modifier.width(7.dp))
                    Text(r.name, color = if (r.done) p.secondary else p.text, fontSize = 11.sp,
                        maxLines = 1, modifier = Modifier.weight(1f))
                    if (r.qty != null) Text(r.qty!!, color = p.secondary, fontSize = 10.sp)
                }
            }
        }
    }

    @Composable
    private fun TodayPreview(p: WidgetPalette, dark: Boolean) {
        val completed = rows.count { it.done }
        val percent = completed * 100 / rows.size
        Column(
            Modifier
                .size(330.dp, 160.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(p.background)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("今日", color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("$completed/${rows.size}", color = p.secondary, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.track)) {
                    Box(Modifier.fillMaxWidth(completed.toFloat() / rows.size).height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(p.primary))
                }
                Spacer(Modifier.width(8.dp))
                Text("$percent%", color = p.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            rows.take(3).forEach { r ->
                Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(CircleShape)
                        .background(if (r.done) p.primary else p.track), contentAlignment = Alignment.Center) {
                        if (r.done) {
                            Text("✓", color = p.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Box(Modifier.size(16.dp).clip(CircleShape).background(p.background))
                        }
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, color = if (r.done) p.secondary else p.text, fontSize = 12.sp,
                            maxLines = 1)
                        if (r.qty != null) Text(r.qty!!, color = p.secondary, fontSize = 10.sp)
                    }
                    if (r.action != null) {
                        Box(Modifier.clip(RoundedCornerShape(9.dp))
                            .background(p.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 9.dp, vertical = 5.dp)) {
                            Text(r.action!!, color = p.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFFEFEFEF)), contentAlignment = Alignment.Center) {
                content()
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        val a = rule.activity
        val v = a.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(
            if (v.width > 0) v.width else 1080,
            if (v.height > 0) v.height else 2340,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        a.runOnUiThread { v.draw(android.graphics.Canvas(bmp)) }
        rule.waitForIdle()
        File("build/widgets").mkdirs()
        File("build/widgets/$name.png").outputStream().use {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun progressLight() = shot("progress-light") { ProgressPreview(palette, false) }
    @Test fun progressDark() = shot("progress-dark") { ProgressPreview(darkPalette, true) }
    @Test fun todayLight() = shot("today-light") { TodayPreview(palette, false) }
    @Test fun todayDark() = shot("today-dark") { TodayPreview(darkPalette, true) }
}

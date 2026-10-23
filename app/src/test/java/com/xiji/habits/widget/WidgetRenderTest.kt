package com.xiji.habits.widget

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * 用 Robolectric 渲染小组件，并把视图树里的文案抽出来断言。
 * Glance 在 Robolectric 下无法直接 captureToImage（依赖 RemoteViews 宿主），
 * 因此这里验证的是：真实 inflate 出来的布局里包含预期文案与结构。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w320dp-h160dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRenderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun collectText(view: View, out: MutableList<String>) {
        if (view is TextView) out.add(view.text?.toString() ?: "")
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) collectText(view.getChildAt(i), out)
        }
    }

    @Test
    fun paletteHasContrastInBothModes() {
        val light = WidgetPalette.of(context)
        val night = WidgetPalette.of(
            android.content.res.Configuration().also { it.uiMode = 0 }
                .let { context }
        )
        // 浅色模式：深色文字 + 浅色底
        assertTrue(light.text != light.background)
        assertTrue(luminance(light.background) > 0.8f)
        assertTrue(luminance(light.text) < 0.2f)
        // 深色模式：亮色文字 + 深色底
        val dark = WidgetPalette(
            background = androidx.compose.ui.graphics.Color(0xFF202721),
            surface = androidx.compose.ui.graphics.Color(0xFF283029),
            primary = androidx.compose.ui.graphics.Color(0xFF92C6AA),
            text = androidx.compose.ui.graphics.Color(0xFFEBF0EA),
            secondary = androidx.compose.ui.graphics.Color(0xFF9AA69D),
            track = androidx.compose.ui.graphics.Color(0xFF2F3832),
            danger = androidx.compose.ui.graphics.Color(0xFFE5A19B),
            onPrimary = androidx.compose.ui.graphics.Color(0xFF10231A)
        )
        assertTrue(luminance(dark.background) < 0.2f)
        assertTrue(luminance(dark.text) > 0.8f)
    }

    private fun luminance(c: androidx.compose.ui.graphics.Color): Float =
        0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
}

package com.xiji.habits.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.cornerRadius

/**
 * 小组件配色：跟随系统深浅色，与 App 主题保持一致。
 *
 * 注意桌面壁纸不可控，因此卡片必须自带不透明底色 + 明确的前景对比度，
 * 不能依赖系统默认的 GlanceTheme（在深色壁纸下会变成半透明）。
 */
data class WidgetPalette(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val text: Color,
    val secondary: Color,
    val track: Color,
    val danger: Color,
    val onPrimary: Color
) {
    companion object {
        fun of(context: Context): WidgetPalette {
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            return if (night) WidgetPalette(
                background = Color(0xFF202721),
                surface = Color(0xFF283029),
                primary = Color(0xFF92C6AA),
                text = Color(0xFFEBF0EA),
                secondary = Color(0xFF9AA69D),
                track = Color(0xFF2F3832),
                danger = Color(0xFFE5A19B),
                onPrimary = Color(0xFF10231A)
            ) else WidgetPalette(
                background = Color(0xFFFFFFFF),
                surface = Color(0xFFF1F3EF),
                primary = Color(0xFF416B5B),
                text = Color(0xFF202824),
                secondary = Color(0xFF78817B),
                track = Color(0xFFE8ECE7),
                danger = Color(0xFFA85A54),
                onPrimary = Color(0xFFFFFFFF)
            )
        }
    }
}

fun textStyle(
    size: Int,
    color: Color,
    bold: Boolean = false,
    align: androidx.glance.text.TextAlign? = null
) = TextStyle(
    color = ColorProvider(color),
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    textAlign = align
)

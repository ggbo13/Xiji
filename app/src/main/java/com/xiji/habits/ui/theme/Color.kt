package com.xiji.habits.ui.theme

import androidx.compose.ui.graphics.Color

// 基础中性色
val LightBackground = Color(0xFFF7F8F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F3EF)
val LightOnBackground = Color(0xFF202824)
val LightOnSurfaceVariant = Color(0xFF78817B)
val LightOutline = Color(0xFFE8ECE7)
val LightOutlineStrong = Color(0xFFD6DCD5)

val DarkBackground = Color(0xFF141916)
val DarkSurface = Color(0xFF202721)
val DarkSurfaceVariant = Color(0xFF283029)
val DarkOnBackground = Color(0xFFEBF0EA)
val DarkOnSurfaceVariant = Color(0xFF9AA69D)
val DarkOutline = Color(0xFF2F3832)
val DarkOutlineStrong = Color(0xFF3C463F)

/** 主题强调色定义 */
enum class AccentTheme(val key: String, val label: String, val light: Color, val lightContainer: Color, val dark: Color, val darkContainer: Color) {
    FOREST("forest", "森林绿", Color(0xFF416B5B), Color(0xFFE4EFE8), Color(0xFF92C6AA), Color(0xFF2A3D34)),
    MIST("mist", "雾霾蓝", Color(0xFF476B82), Color(0xFFE3EDF3), Color(0xFF9CC4DC), Color(0xFF27343D)),
    ORANGE("orange", "暖橘色", Color(0xFF9C5A34), Color(0xFFF6E9DE), Color(0xFFE2AC86), Color(0xFF3D2E23)),
    LAVENDER("lavender", "淡紫色", Color(0xFF6A5B85), Color(0xFFEDE8F4), Color(0xFFC0B2DD), Color(0xFF312B3D)),
    GRAPHITE("graphite", "石墨灰", Color(0xFF4E5651), Color(0xFFEAEDEA), Color(0xFFB4BDB7), Color(0xFF2B302D));

    companion object {
        fun of(key: String?): AccentTheme = entries.firstOrNull { it.key == key } ?: FOREST
    }
}

/** 习惯可选颜色（用于图标与进度强调） */
val HabitPalette: List<Long> = listOf(
    0xFF416B5B, 0xFF5B7FA6, 0xFFB5713F, 0xFF8A6EA8, 0xFF4F7D74,
    0xFF9A5B5B, 0xFF7A7F55, 0xFF4A6572, 0xFFA5765C, 0xFF6C6F78
)

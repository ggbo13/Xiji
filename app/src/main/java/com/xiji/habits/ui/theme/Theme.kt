package com.xiji.habits.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 除 Material 颜色之外的语义色，供页面背景/卡片/分割线使用 */
data class XijiColors(
    val pageBackground: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val divider: Color,
    val subtleFill: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val isDark: Boolean
)

val LocalXijiColors = staticCompositionLocalOf {
    XijiColors(
        pageBackground = LightBackground,
        cardBackground = LightSurface,
        cardBorder = LightOutline,
        primaryText = LightOnBackground,
        secondaryText = LightOnSurfaceVariant,
        divider = LightOutline,
        subtleFill = LightSurfaceVariant,
        success = Color(0xFF3E7D5A),
        warning = Color(0xFFA97A3B),
        danger = Color(0xFFA85A54),
        isDark = false
    )
}

@Composable
fun XijiTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentTheme = AccentTheme.FOREST,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val baseScheme = when {
        dynamicColor && supportsDynamic && dark -> dynamicDarkColorScheme(context)
        dynamicColor && supportsDynamic -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = accent.dark,
            onPrimary = Color(0xFF10231A),
            primaryContainer = accent.darkContainer,
            onPrimaryContainer = accent.dark,
            secondary = accent.dark,
            onSecondary = Color(0xFF10231A),
            secondaryContainer = accent.darkContainer,
            onSecondaryContainer = accent.dark,
            background = DarkBackground,
            onBackground = DarkOnBackground,
            surface = DarkSurface,
            onSurface = DarkOnBackground,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            surfaceContainer = DarkSurface,
            surfaceContainerHigh = DarkSurfaceVariant,
            surfaceContainerLow = DarkBackground,
            outline = DarkOutlineStrong,
            outlineVariant = DarkOutline,
            error = Color(0xFFE5A19B),
            onError = Color(0xFF3A1512),
            scrim = Color(0xFF000000)
        )
        else -> lightColorScheme(
            primary = accent.light,
            onPrimary = Color.White,
            primaryContainer = accent.lightContainer,
            onPrimaryContainer = accent.light,
            secondary = accent.light,
            onSecondary = Color.White,
            secondaryContainer = accent.lightContainer,
            onSecondaryContainer = accent.light,
            background = LightBackground,
            onBackground = LightOnBackground,
            surface = LightSurface,
            onSurface = LightOnBackground,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            surfaceContainer = LightSurface,
            surfaceContainerHigh = LightSurfaceVariant,
            surfaceContainerLow = LightBackground,
            outline = LightOutlineStrong,
            outlineVariant = LightOutline,
            error = Color(0xFFA85A54),
            onError = Color.White,
            scrim = Color(0xFF000000)
        )
    }

    val xiji = if (dark) XijiColors(
        pageBackground = DarkBackground,
        cardBackground = DarkSurface,
        cardBorder = DarkOutline,
        primaryText = DarkOnBackground,
        secondaryText = DarkOnSurfaceVariant,
        divider = DarkOutline,
        subtleFill = DarkSurfaceVariant,
        success = Color(0xFF8FC7A6),
        warning = Color(0xFFD8B07A),
        danger = Color(0xFFE5A19B),
        isDark = true
    ) else XijiColors(
        pageBackground = LightBackground,
        cardBackground = LightSurface,
        cardBorder = LightOutline,
        primaryText = LightOnBackground,
        secondaryText = LightOnSurfaceVariant,
        divider = LightOutline,
        subtleFill = LightSurfaceVariant,
        success = Color(0xFF3E7D5A),
        warning = Color(0xFFA97A3B),
        danger = Color(0xFFA85A54),
        isDark = false
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalXijiColors provides xiji) {
        MaterialTheme(
            colorScheme = baseScheme,
            typography = XijiTypography,
            content = content
        )
    }
}

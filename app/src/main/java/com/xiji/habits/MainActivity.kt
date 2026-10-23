package com.xiji.habits

import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.xiji.habits.widget.WidgetRefresh
import androidx.compose.ui.platform.LocalContext
import com.xiji.habits.data.preferences.UserSettings
import com.xiji.habits.ui.XijiApp
import com.xiji.habits.ui.theme.XijiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* 无论是否授予，核心功能都可用 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        val container = (application as XijiApplication).container
        setContent {
            val settings by container.settingsRepository.settings
                .collectAsState(initial = UserSettings())
            XijiTheme(
                themeMode = settings.themeMode,
                accent = settings.accent,
                dynamicColor = settings.dynamicColor
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.xiji.habits.ui.theme.LocalReduceMotion provides settings.reduceAnimation
                ) {
                    XijiApp(container = container, reduceAnimation = settings.reduceAnimation)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到前台时刷新小组件：覆盖「App 被杀后重新打开」等错过变更信号的场景
        lifecycleScope.launch { WidgetRefresh.refreshNow(this@MainActivity) }
    }

    /** Android 13+ 通知权限：仅在用户已设置提醒时才主动询问一次 */
    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) return
        val asked = getSharedPreferences("xiji_permission", MODE_PRIVATE)
            .getBoolean("notification_asked", false)
        if (asked) return
        getSharedPreferences("xiji_permission", MODE_PRIVATE).edit()
            .putBoolean("notification_asked", true).apply()
        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

package com.xiji.habits.ui.settings

import androidx.compose.material.icons.rounded.Add
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiji.habits.data.preferences.UserSettings
import com.xiji.habits.data.preferences.WeekStartSetting
import com.xiji.habits.domain.model.HabitCategory
import com.xiji.habits.ui.components.HairlineDivider
import com.xiji.habits.ui.components.SectionHeader
import com.xiji.habits.ui.components.XijiCard
import com.xiji.habits.ui.theme.AccentTheme
import com.xiji.habits.ui.theme.HabitPalette
import com.xiji.habits.ui.theme.LocalXijiColors
import com.xiji.habits.ui.theme.ThemeMode

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    state: SettingsUiState,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (String) -> Unit,
    onDynamic: (Boolean) -> Unit,
    onWeekStart: (WeekStartSetting) -> Unit,
    onReduceAnimation: (Boolean) -> Unit,
    onOpenBackup: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenWidgetHelp: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onSaveCategory: (String, Long) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onEditCategory: (HabitCategory) -> Unit,
    onConsumeMessage: () -> Unit,
    onOpenCategoryManager: () -> Unit = {},
    onCloseCategoryManager: () -> Unit = {},
    onRequestDeleteCategory: (HabitCategory) -> Unit = {},
    onCancelDeleteCategory: () -> Unit = {},
    onConfirmDeleteCategory: () -> Unit = {},
    onCreateCategory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalXijiColors.current
    val context = LocalContext.current
    var categoryName by remember(state.showCategoryDialog) { mutableStateOf(state.editingCategory?.name ?: "") }
    var categoryColor by remember(state.showCategoryDialog) { mutableStateOf(state.editingCategory?.color ?: HabitPalette.first()) }

    Box(modifier.fillMaxSize().background(colors.pageBackground)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)
        ) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                    Text("设置", style = MaterialTheme.typography.headlineMedium, color = colors.primaryText)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "所有数据只保存在这台设备上。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                }
            }

            // 外观
            item {
                Spacer(Modifier.height(18.dp))
                SectionHeader("外观")
                Spacer(Modifier.height(8.dp))
                XijiCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("主题", style = MaterialTheme.typography.bodyMedium, color = colors.primaryText)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SegmentOption("跟随系统", settings.themeMode == ThemeMode.SYSTEM, Modifier.weight(1f)) { onThemeMode(ThemeMode.SYSTEM) }
                            SegmentOption("浅色", settings.themeMode == ThemeMode.LIGHT, Modifier.weight(1f)) { onThemeMode(ThemeMode.LIGHT) }
                            SegmentOption("深色", settings.themeMode == ThemeMode.DARK, Modifier.weight(1f)) { onThemeMode(ThemeMode.DARK) }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("强调色", style = MaterialTheme.typography.bodyMedium, color = colors.primaryText)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AccentTheme.entries.forEach { accent ->
                                val selected = settings.accentKey == accent.key
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(accent.light)
                                            .border(
                                                width = if (selected) 2.dp else 0.dp,
                                                color = if (selected) colors.primaryText else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { onAccent(accent.key) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        accent.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) colors.primaryText else colors.secondaryText
                                    )
                                }
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Spacer(Modifier.height(14.dp))
                            HairlineDivider()
                            Spacer(Modifier.height(12.dp))
                            SwitchRow(
                                title = "动态取色",
                                subtitle = "使用系统壁纸配色（Android 12+）",
                                checked = settings.dynamicColor,
                                onCheckedChange = onDynamic
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        HairlineDivider()
                        Spacer(Modifier.height(12.dp))
                        SwitchRow(
                            title = "减少动画",
                            subtitle = "关闭过渡与弹跳效果",
                            checked = settings.reduceAnimation,
                            onCheckedChange = onReduceAnimation
                        )
                    }
                }
            }

            // 习惯与数据
            item {
                Spacer(Modifier.height(18.dp))
                SectionHeader("习惯与数据")
                Spacer(Modifier.height(8.dp))
                XijiCard(Modifier.fillMaxWidth()) {
                    Column {
                        SettingRow("分类管理", "${state.categories.size} 个分类", onOpenCategoryManager)
                        HairlineDivider()
                        SettingRow("每周起始日", if (settings.weekStart == WeekStartSetting.MONDAY) "周一" else "周日") {
                            onWeekStart(
                                if (settings.weekStart == WeekStartSetting.MONDAY) WeekStartSetting.SUNDAY
                                else WeekStartSetting.MONDAY
                            )
                        }
                        HairlineDivider()
                        SettingRow("备份与恢复", "JSON / CSV，本地文件", onOpenBackup)
                        HairlineDivider()
                        SettingRow("桌面小组件", "2×2 与 4×2", onOpenWidgetHelp)
                        HairlineDivider()
                        Row(Modifier.padding(16.dp)) {
                            Text("本地数据", style = MaterialTheme.typography.bodyMedium, color = colors.primaryText, modifier = Modifier.weight(1f))
                            Text(
                                "${state.habitCount} 个习惯 · ${state.recordCount} 条记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                        }
                    }
                }
            }

            // 提醒
            item {
                Spacer(Modifier.height(18.dp))
                SectionHeader("提醒")
                Spacer(Modifier.height(8.dp))
                XijiCard(Modifier.fillMaxWidth()) {
                    Column {
                        SettingRow("通知设置", "Android 13+ 需要通知权限", onOpenNotificationSettings)
                        HairlineDivider()
                        SettingRow(
                            "精确提醒",
                            if (state.exactAlarmAllowed) "已允许" else "未允许，可能延迟",
                            onOpenExactAlarmSettings
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "提醒完全由系统本地调度，不使用任何云推送服务。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // 隐私
            item {
                Spacer(Modifier.height(18.dp))
                SectionHeader("隐私")
                Spacer(Modifier.height(8.dp))
                XijiCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        PrivacyLine("不联网", "应用未申请 INTERNET 权限，飞行模式下可完整使用。")
                        Spacer(Modifier.height(10.dp))
                        PrivacyLine("无账号", "不需要注册、登录，也没有任何第三方统计 SDK。")
                        Spacer(Modifier.height(10.dp))
                        PrivacyLine("禁止云备份", "已关闭系统自动备份（allowBackup=false），数据不会被上传。")
                        Spacer(Modifier.height(10.dp))
                        PrivacyLine("主动导出", "只有你手动选择文件位置时才会写出数据；若选择云盘目录，同步由该系统应用负责。")
                    }
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                XijiCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("习迹 1.0.0", style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "每一点坚持，都算数。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "本应用完全离线运行，数据保存在应用私有目录的 SQLite 数据库中。卸载应用会删除数据，请定期导出备份。",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText
                        )
                    }
                }
            }
        }
    }

    if (state.showCategoryManager) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = onCloseCategoryManager,
            containerColor = colors.cardBackground,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("分类管理", style = MaterialTheme.typography.titleLarge, color = colors.primaryText)
                        Text(
                            "删除分类不会删除其中的习惯",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText
                        )
                    }
                    TextButton(onClick = onCreateCategory) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("新建")
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (state.categories.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "还没有分类。分类可以用来给习惯分组筛选。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                    }
                } else {
                    state.categories.forEach { category ->
                        val count = state.categoryUsage[category.id] ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onEditCategory(category) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(category.color))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    category.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.primaryText
                                )
                                Text(
                                    if (count > 0) "$count 个习惯" else "暂无习惯",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondaryText
                                )
                            }
                            TextButton(onClick = { onEditCategory(category) }) { Text("编辑") }
                            TextButton(onClick = { onRequestDeleteCategory(category) }) {
                                Text("删除", color = colors.danger)
                            }
                        }
                        HairlineDivider()
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "提示：删除分类后，原来属于它的习惯会变成「未分类」，打卡记录不受影响。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText
                )
            }
        }
    }

    state.pendingDelete?.let { target ->
        val count = state.categoryUsage[target.id] ?: 0
        AlertDialog(
            onDismissRequest = onCancelDeleteCategory,
            title = { Text("删除分类「${target.name}」？") },
            text = {
                Text(
                    if (count > 0) "这个分类下有 $count 个习惯。删除后它们会变成「未分类」，习惯本身和全部打卡记录都会保留。"
                    else "这个分类下没有习惯，可以安全删除。"
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDeleteCategory) {
                    Text("删除分类", color = colors.danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDeleteCategory) { Text("取消") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (state.showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { onEditCategory(HabitCategory(-1, "", 0, 0)) },
            title = { Text(if (state.editingCategory == null) "新建分类" else "编辑分类") },
            text = {
                Column {
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it.take(20) },
                        label = { Text("分类名称") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HabitPalette.take(7).forEach { c ->
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .clickable { categoryColor = c },
                                contentAlignment = Alignment.Center
                            ) {
                                if (categoryColor == c) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    if (state.editingCategory != null && state.editingCategory.id > 0) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { onDeleteCategory(state.editingCategory.id) }) {
                            Text("删除这个分类", color = colors.danger)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSaveCategory(categoryName, categoryColor) }) {
                    Text("保存", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEditCategory(HabitCategory(-1, "", 0, 0)) }) { Text("取消") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun SegmentOption(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalXijiColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else colors.subtleFill)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else colors.secondaryText,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalXijiColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.primaryText)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = LocalXijiColors.current.secondaryText,
                                        uncheckedTrackColor = LocalXijiColors.current.subtleFill,
                                        uncheckedBorderColor = LocalXijiColors.current.cardBorder
                                    )
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    val colors = LocalXijiColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.primaryText)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.secondaryText,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun PrivacyLine(title: String, body: String) {
    val colors = LocalXijiColors.current
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
        Text(body, style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
    }
}

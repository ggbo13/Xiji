package com.xiji.habits.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiji.habits.ui.components.HairlineDivider
import com.xiji.habits.ui.components.SectionHeader
import com.xiji.habits.ui.components.XijiCard
import com.xiji.habits.ui.theme.LocalXijiColors

@Composable
fun BackupScreen(
    state: BackupUiState,
    defaultJsonName: String,
    defaultCsvName: String,
    onBack: () -> Unit,
    onExportJson: (Uri) -> Unit,
    onExportCsv: (Uri) -> Unit,
    onPickImport: (Uri) -> Unit,
    onSetReplace: (Boolean) -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalXijiColors.current

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExportJson) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(onExportCsv) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onPickImport) }

    Box(modifier.fillMaxSize().background(colors.pageBackground)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)
        ) {
            item {
                Row(
                    Modifier.statusBarsPadding().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = colors.primaryText)
                    }
                    Text("备份与恢复", style = MaterialTheme.typography.titleLarge, color = colors.primaryText)
                    Spacer(Modifier.weight(1f))
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            item {
                XijiCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp)) {
                        Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("完全本地", style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "导出时由系统文件选择器决定保存位置，App 自身不会上传任何数据。如果你选择的是云盘目录，同步行为由该文件提供方负责。",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                SectionHeader("导出")
                Spacer(Modifier.height(8.dp))
                XijiCard(Modifier.fillMaxWidth()) {
                    Column {
                        ExportRow(
                            icon = Icons.Rounded.Download,
                            title = "完整备份（JSON）",
                            subtitle = "包含习惯、记录、分类、提醒与设置",
                            onClick = { jsonLauncher.launch(defaultJsonName) }
                        )
                        HairlineDivider()
                        ExportRow(
                            icon = Icons.Rounded.Upload,
                            title = "历史记录（CSV）",
                            subtitle = "便于在表格软件中查看，不包含设置",
                            onClick = { csvLauncher.launch(defaultCsvName) }
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                SectionHeader("恢复")
                Spacer(Modifier.height(8.dp))
                XijiCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("从 JSON 备份恢复", style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "导入前会先校验文件格式与版本，并显示待导入数量。写入在单个事务中完成，失败会自动回滚。",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                                .clickable { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "选择备份文件",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "提示：卸载应用会删除本地数据。建议定期导出备份，并自行保管文件。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText,
                    modifier = Modifier.padding(horizontal = 4.dp).navigationBarsPadding()
                )
            }
        }
    }

    state.message?.let { msg ->
        AlertDialog(
            onDismissRequest = onConsumeMessage,
            confirmButton = { TextButton(onClick = onConsumeMessage) { Text("好") } },
            title = { Text(if (state.isError) "操作未完成" else "完成") },
            text = { Text(msg) },
            shape = RoundedCornerShape(24.dp)
        )
    }

    val preview = state.preview
    if (preview != null) {
        AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text("确认导入") },
            text = {
                Column {
                    Text("备份版本 v${preview.schemaVersion} · 导出于 ${preview.exportedAtIso.take(10)}")
                    Spacer(Modifier.height(8.dp))
                    Text("将导入 ${preview.habitCount} 个习惯、${preview.recordCount} 条记录、${preview.categoryCount} 个分类。")
                    if (preview.duplicateIds > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "其中 ${preview.duplicateIds} 个习惯与现有 ID 重复，合并模式会为它们分配新 ID。",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onSetReplace(false) }) {
                        RadioButton(
                            selected = !state.replaceMode,
                            onClick = { onSetReplace(false) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text("合并到现有数据", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onSetReplace(true) }) {
                        RadioButton(
                            selected = state.replaceMode,
                            onClick = { onSetReplace(true) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text("替换全部现有数据", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (state.replaceMode) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "替换会先清空当前数据，建议先导出一份备份。",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.danger
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onConfirmImport) { Text("导入", fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = onCancelImport) { Text("取消") } },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun ExportRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = LocalXijiColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.primaryText)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
        }
    }
}

package com.xiji.habits.ui.habits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiji.habits.domain.model.HabitType
import com.xiji.habits.domain.model.ScheduleType
import com.xiji.habits.ui.components.FieldShape
import com.xiji.habits.ui.components.HairlineDivider
import com.xiji.habits.ui.components.HabitIcons
import com.xiji.habits.ui.components.PillShape
import com.xiji.habits.ui.components.SectionHeader
import com.xiji.habits.ui.components.XijiCard
import com.xiji.habits.ui.theme.HabitPalette
import com.xiji.habits.ui.theme.LocalXijiColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditScreen(
    state: EditUiState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onDescription: (String) -> Unit,
    onIcon: (String) -> Unit,
    onColor: (Long) -> Unit,
    onCategory: (Long?) -> Unit,
    onType: (HabitType) -> Unit,
    onTargetValue: (String) -> Unit,
    onTargetUnit: (String) -> Unit,
    onScheduleType: (ScheduleType) -> Unit,
    onToggleWeekday: (Int) -> Unit,
    onTimesPerWeek: (Int) -> Unit,
    onIntervalDays: (Int) -> Unit,
    onStartDate: (LocalDate) -> Unit,
    onToggleAdvanced: () -> Unit,
    onConfirmQuitDaily: (Boolean) -> Unit,
    onAddReminder: () -> Unit,
    onUpdateReminder: (Int, Int, Int) -> Unit,
    onToggleReminder: (Int) -> Unit,
    onRemoveReminder: (Int) -> Unit,
    onSave: () -> Unit,
    onSaved: () -> Unit
) {
    val colors = LocalXijiColors.current
    var timePickerIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = colors.primaryText)
            }
            Text(
                if (state.isEditing) "编辑习惯" else "新建习惯",
                style = MaterialTheme.typography.titleLarge,
                color = colors.primaryText,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onSave,
                enabled = !state.saving
            ) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                XijiCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader("基础信息")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = onName,
                            label = { Text("习惯名称") },
                            placeholder = { Text("例如：阅读 30 分钟") },
                            isError = state.nameError != null,
                            supportingText = state.nameError?.let { { Text(it) } },
                            singleLine = true,
                            shape = FieldShape,
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.description,
                            onValueChange = onDescription,
                            label = { Text("描述（可选）") },
                            placeholder = { Text("想清楚为什么要坚持它") },
                            minLines = 2,
                            maxLines = 4,
                            shape = FieldShape,
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))
                        Text("图标", style = MaterialTheme.typography.labelMedium, color = colors.secondaryText)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HabitIcons.options.forEach { opt ->
                                val selected = state.icon == opt.key
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else colors.subtleFill)
                                        .border(
                                            width = if (selected) 1.5.dp else 0.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onIcon(opt.key) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(opt.emoji, fontSize = 19.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("颜色", style = MaterialTheme.typography.labelMedium, color = colors.secondaryText)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HabitPalette.take(7).forEach { c ->
                                val selected = state.color == c
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                        .clickable { onColor(c) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = "已选择",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                XijiCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader("习惯类型")
                        Spacer(Modifier.height(12.dp))
                        TypeSelector(state.type, onType)
                        if (state.type == HabitType.QUANTITY) {
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                OutlinedTextField(
                                    value = state.targetValue,
                                    onValueChange = onTargetValue,
                                    label = { Text("每日目标") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    isError = state.targetError != null,
                                    supportingText = state.targetError?.let { { Text(it) } },
                                    singleLine = true,
                                    shape = FieldShape,
                                    colors = fieldColors(),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(10.dp))
                                OutlinedTextField(
                                    value = state.targetUnit,
                                    onValueChange = onTargetUnit,
                                    label = { Text("单位") },
                                    placeholder = { Text("分钟") },
                                    singleLine = true,
                                    shape = FieldShape,
                                    colors = fieldColors(),
                                    modifier = Modifier.width(110.dp)
                                )
                            }
                        }
                        if (state.type == HabitType.QUIT) {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("每天主动确认", style = MaterialTheme.typography.bodyMedium, color = colors.primaryText)
                                    Text(
                                        "不打开 App 不会被自动判定为成功",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryText
                                    )
                                }
                                Switch(
                                    checked = state.confirmQuitDaily,
                                    onCheckedChange = onConfirmQuitDaily,
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
                    }
                }
            }

            item {
                XijiCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader("执行周期")
                        Spacer(Modifier.height(12.dp))
                        ScheduleSelector(state, onScheduleType, onToggleWeekday, onTimesPerWeek, onIntervalDays)
                        Spacer(Modifier.height(12.dp))
                        HairlineDivider()
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("开始日期", style = MaterialTheme.typography.bodyMedium, color = colors.primaryText)
                                Text(
                                    "${state.startDate.year}年${state.startDate.monthValue}月${state.startDate.dayOfMonth}日",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                            TextButton(onClick = { onStartDate(LocalDate.now()) }) { Text("设为今天") }
                        }
                    }
                }
            }

            item {
                XijiCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleAdvanced),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("分类与提醒", style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
                                Text(
                                    if (state.reminders.isEmpty()) "未设置提醒" else "${state.reminders.size} 个提醒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                            Icon(
                                if (state.advancedExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (state.advancedExpanded) "收起" else "展开",
                                tint = colors.secondaryText
                            )
                        }
                        AnimatedVisibility(
                            visible = state.advancedExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                Spacer(Modifier.height(14.dp))
                                Text("分类", style = MaterialTheme.typography.labelMedium, color = colors.secondaryText)
                                Spacer(Modifier.height(8.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CategoryChip("未分类", state.categoryId == null) { onCategory(null) }
                                    state.categories.forEach { c ->
                                        CategoryChip(c.name, state.categoryId == c.id) { onCategory(c.id) }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("提醒时间", style = MaterialTheme.typography.labelMedium, color = colors.secondaryText, modifier = Modifier.weight(1f))
                                    if (state.reminders.size < 4) {
                                        TextButton(onClick = onAddReminder) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("添加")
                                        }
                                    }
                                }
                                state.reminders.forEachIndexed { index, r ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(colors.subtleFill)
                                                .clickable { timePickerIndex = index }
                                                .padding(horizontal = 14.dp, vertical = 9.dp)
                                        ) {
                                            Text(
                                                "%02d:%02d".format(r.hour, r.minute),
                                                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                                                color = colors.primaryText
                                            )
                                        }
                                        Spacer(Modifier.weight(1f))
                                        Switch(
                                            checked = r.enabled,
                                            onCheckedChange = { onToggleReminder(index) },
                                            colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = LocalXijiColors.current.secondaryText,
                                        uncheckedTrackColor = LocalXijiColors.current.subtleFill,
                                        uncheckedBorderColor = LocalXijiColors.current.cardBorder
                                    )
                                        )
                                        IconButton(onClick = { onRemoveReminder(index) }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "移除提醒", tint = colors.secondaryText, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = onSave,
                    enabled = !state.saving,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().height(50.dp).navigationBarsPadding()
                ) {
                    Text(if (state.isEditing) "保存修改" else "创建习惯", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    timePickerIndex?.let { idx ->
        val r = state.reminders.getOrNull(idx)
        if (r != null) {
            val tpState = rememberTimePickerState(initialHour = r.hour, initialMinute = r.minute, is24Hour = true)
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { timePickerIndex = null },
                confirmButton = {
                    TextButton(onClick = {
                        onUpdateReminder(idx, tpState.hour, tpState.minute)
                        timePickerIndex = null
                    }) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = { timePickerIndex = null }) { Text("取消") } },
                title = { Text("提醒时间") },
                text = { TimePicker(state = tpState) },
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = LocalXijiColors.current.cardBorder,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)

@Composable
private fun TypeSelector(current: HabitType, onSelect: (HabitType) -> Unit) {
    val colors = LocalXijiColors.current
    val items = listOf(
        Triple(HabitType.COMPLETE, "完成型", "只需标记完成"),
        Triple(HabitType.QUANTITY, "数量型", "累计到目标值"),
        Triple(HabitType.QUIT, "戒除型", "记录是否守住")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (type, title, desc) ->
            val selected = current == type
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else colors.subtleFill)
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(type) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else colors.primaryText
                    )
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                }
                if (selected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleSelector(
    state: EditUiState,
    onType: (ScheduleType) -> Unit,
    onToggleWeekday: (Int) -> Unit,
    onTimesPerWeek: (Int) -> Unit,
    onIntervalDays: (Int) -> Unit
) {
    val colors = LocalXijiColors.current
    val types = listOf(
        ScheduleType.DAILY to "每天",
        ScheduleType.WEEKDAYS to "指定星期",
        ScheduleType.TIMES_PER_WEEK to "每周次数",
        ScheduleType.INTERVAL to "隔天"
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { (t, label) ->
            val selected = state.scheduleType == t
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else colors.subtleFill)
                    .clickable { onType(t) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else colors.secondaryText
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    when (state.scheduleType) {
        ScheduleType.WEEKDAYS -> {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, label ->
                    val on = (state.weekdaysMask shr i) and 1 == 1
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primaryContainer else colors.subtleFill)
                            .clickable { onToggleWeekday(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) MaterialTheme.colorScheme.primary else colors.secondaryText
                        )
                    }
                }
            }
        }
        ScheduleType.TIMES_PER_WEEK -> {
            StepperRow("每周目标", "${state.timesPerWeek} 次") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { n ->
                        val on = state.timesPerWeek == n
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else colors.subtleFill)
                                .clickable { onTimesPerWeek(n) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$n",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else colors.secondaryText
                            )
                        }
                    }
                }
            }
        }
        ScheduleType.INTERVAL -> {
            StepperRow("间隔天数", "每 ${state.intervalDays} 天") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 3, 4, 5, 7).forEach { n ->
                        val on = state.intervalDays == n
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else colors.subtleFill)
                                .clickable { onIntervalDays(n) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "$n 天",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else colors.secondaryText
                            )
                        }
                    }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun StepperRow(label: String, value: String, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalXijiColors.current.secondaryText)
            Spacer(Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall, color = LocalXijiColors.current.primaryText)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun CategoryChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalXijiColors.current
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else colors.subtleFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else colors.secondaryText
        )
    }
}

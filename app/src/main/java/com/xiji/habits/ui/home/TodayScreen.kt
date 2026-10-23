package com.xiji.habits.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.xiji.habits.domain.model.DayState
import com.xiji.habits.domain.model.Habit
import com.xiji.habits.ui.components.CardShape
import com.xiji.habits.ui.components.EmptyState
import com.xiji.habits.ui.components.PillLabel
import com.xiji.habits.ui.components.SmoothProgressBar
import com.xiji.habits.ui.components.XijiCard
import com.xiji.habits.ui.theme.LocalReduceMotion
import com.xiji.habits.ui.theme.LocalXijiColors
import com.xiji.habits.ui.theme.XijiMotion
import java.time.LocalDate

@Composable
fun TodayScreen(
    state: HomeUiState,
    onSelectDate: (LocalDate) -> Unit,
    onToggle: (Habit) -> Unit,
    onOpenDetail: (Long) -> Unit,
    onLongPress: (Habit) -> Unit,
    onAdd: () -> Unit,
    onFilter: (HabitFilter) -> Unit,
    onUndo: () -> Unit,
    onConsumeSnack: () -> Unit,
    onToggleCollapse: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onAddValue: (Habit, Double) -> Unit = { _, _ -> },
    onMarkFailed: (Habit) -> Unit = {},
    onSkip: (Habit) -> Unit = {},
    onArchive: (Habit) -> Unit = {},
    onDelete: (Habit) -> Unit = {},
    onReorder: (Long, Long) -> Unit = { _, _ -> },
    onReorderAll: (List<Long>) -> Unit = {},
    onCategory: (Long?) -> Unit = { }
) {
    val colors = LocalXijiColors.current
    val isToday = state.selectedDate == state.today
    var actionTarget by remember { mutableStateOf<Habit?>(null) }
    var quickAddTarget by remember { mutableStateOf<Habit?>(null) }
    var deleteTarget by remember { mutableStateOf<Habit?>(null) }
    var reorderMode by remember { mutableStateOf(false) }

    val reduceMotion = LocalReduceMotion.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val pendingRows = remember(state.rows) { state.rows.filter { it.state != DayState.DONE } }
    val doneRows = remember(state.rows) { state.rows.filter { it.state == DayState.DONE } }

    // 松手提交时要用「当前」的回调与数据，避免 remember 里捕获到旧值
    val commitOrder by rememberUpdatedState(
        { newPending: List<Long> ->
            onReorderAll(
                splicePendingOrder(
                    allIds = state.rows.map { it.habit.id },
                    isPending = state.rows.map { it.state != DayState.DONE },
                    newPendingIds = newPending
                )
            )
        }
    )

    val reorder = remember(listState, reduceMotion) {
        ReorderState(
            listState = listState,
            haptics = haptics,
            scope = scope,
            reduceMotion = reduceMotion,
            edgePx = with(density) { 88.dp.toPx() },
            onCommit = { commitOrder(it) }
        )
    }
    LaunchedEffect(pendingRows, reorderMode) { reorder.sync(pendingRows, reorderMode) }
    // 布局变化后继续检查换位；位移在绘制时直接读取，避免异步修正导致跳动。
    LaunchedEffect(reorder) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == pendingKey(reorder.draggingId) }?.offset
        }.collect { reorder.onLayoutChanged() }
    }

    LaunchedEffect(state.snackMessage) {
        val msg = state.snackMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = if (msg.contains("撤销") || msg.contains("完成")) "撤销" else null,
            duration = androidx.compose.material3.SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) onUndo()
        onConsumeSnack()
    }

    Box(modifier.fillMaxSize().background(colors.pageBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp
            )
        ) {
            item(key = "header") {
                Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                    Text(
                        text = state.dateLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryText
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.greeting,
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.primaryText
                    )
                }
            }

            item(key = "summary") {
                Spacer(Modifier.height(18.dp))
                SummaryCard(state)
            }

            item(key = "week") {
                Spacer(Modifier.height(16.dp))
                WeekStrip(state, onSelectDate)
                if (state.selectedDate.isBefore(state.today)) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.subtleFill)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "正在查看 ${state.selectedDate.monthValue}月${state.selectedDate.dayOfMonth}日 · 打卡即为补签",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "回到今天",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectDate(state.today) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            item(key = "filters") {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { FilterRow(state, onFilter) }
                    if (state.rows.size > 1) {
                        Spacer(Modifier.width(8.dp))
                        val sortSpec = if (reduceMotion) snap<Color>() else tween<Color>(XijiMotion.FAST)
                        val sortBg by animateColorAsState(
                            targetValue = if (reorderMode) MaterialTheme.colorScheme.primaryContainer else colors.subtleFill,
                            animationSpec = sortSpec,
                            label = "sortBg"
                        )
                        val sortFg by animateColorAsState(
                            targetValue = if (reorderMode) MaterialTheme.colorScheme.primary else colors.secondaryText,
                            animationSpec = sortSpec,
                            label = "sortFg"
                        )
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(sortBg)
                                .clickable { reorderMode = !reorderMode }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                if (reorderMode) "完成" else "排序",
                                style = MaterialTheme.typography.labelMedium,
                                color = sortFg,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            item(key = "reorderHint") {
                AnimatedVisibility(
                    visible = reorderMode,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.subtleFill)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.DragIndicator,
                            contentDescription = null,
                            tint = colors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "按住右侧把手上下拖动，松手后自动保存顺序",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText
                        )
                    }
                }
            }

            if (state.categories.isNotEmpty()) {
                item(key = "cats") {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CatChip("全部", state.categoryFilter == null) { onCategory(null) }
                        state.categories.forEach { c ->
                            CatChip(c.name, state.categoryFilter == c.id) { onCategory(c.id) }
                        }
                    }
                }
            }

            if (state.rows.isEmpty()) {
                item(key = "empty") {
                    Spacer(Modifier.height(8.dp))
                    XijiCard(Modifier.fillMaxWidth()) {
                        EmptyState(
                            title = if (state.filter == HabitFilter.ALL) "今天还没有习惯" else "这个分类还没有内容",
                            description = if (state.filter == HabitFilter.ALL)
                                "从一个足够小的目标开始，比如每天读两页书。" else "换一个筛选条件看看。"
                        )
                    }
                }
            } else {
                val pending = reorder.ordered(pendingRows)
                val done = doneRows
                if (pending.isNotEmpty()) {
                    item(key = "pendingHeader") {
                        Spacer(Modifier.height(18.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "待完成",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.secondaryText
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${pending.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.secondaryText
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    itemsIndexed(pending, key = { _, r -> pendingKey(r.habit.id) }, contentType = { _, _ -> "habit" }) { _, row ->
                        val dragging = reorder.draggingId == row.habit.id
                        val moving = dragging || reorder.settlingId == row.habit.id
                        val lift by animateFloatAsState(
                            targetValue = if (dragging) 1f else 0f,
                            animationSpec = XijiMotion.lift(reduceMotion),
                            label = "lift"
                        )
                        Column(
                            modifier = Modifier
                                // 被拖起的卡片不做位移动画，否则会和手指打架
                                .animateItem(
                                    fadeInSpec = XijiMotion.fade(reduceMotion),
                                    placementSpec = if (moving) null else XijiMotion.placement(reduceMotion),
                                    fadeOutSpec = XijiMotion.fade(reduceMotion)
                                )
                                .zIndex(if (moving) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (moving) reorder.translation else 0f
                                    val s = 1f + 0.018f * lift
                                    scaleX = s
                                    scaleY = s
                                }
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        shadowElevation = 14.dp.toPx() * lift
                                        shape = CardShape
                                    }
                            ) {
                                HabitCard(
                                    row = row,
                                    isToday = isToday,
                                    onToggle = onToggle,
                                    onOpenDetail = onOpenDetail,
                                    onLongPress = { actionTarget = it },
                                    onQuickAdd = { quickAddTarget = it },
                                    reorderMode = reorderMode,
                                    dragHandleModifier = reorder.dragModifier(row.habit.id, reorderMode),
                                    dragActions = listOf(
                                        CustomAccessibilityAction("上移") {
                                            reorder.move(row.habit.id, -1); true
                                        },
                                        CustomAccessibilityAction("下移") {
                                            reorder.move(row.habit.id, 1); true
                                        }
                                    )
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
                if (done.isNotEmpty()) {
                    item(key = "doneHeader") {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onToggleCollapse)
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "已完成 ${done.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.secondaryText
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                imageVector = if (state.doneCollapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                contentDescription = if (state.doneCollapsed) "展开已完成" else "收起已完成",
                                tint = colors.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (!state.doneCollapsed) {
                        items(done, key = { "d_${it.habit.id}" }, contentType = { "habit" }) { row ->
                            Column(
                                Modifier.animateItem(
                                    fadeInSpec = XijiMotion.fade(reduceMotion),
                                    placementSpec = XijiMotion.placement(reduceMotion),
                                    fadeOutSpec = XijiMotion.fade(reduceMotion)
                                )
                            ) {
                                HabitCard(
                                    row = row,
                                    isToday = isToday,
                                    onToggle = onToggle,
                                    onOpenDetail = onOpenDetail,
                                    onLongPress = { actionTarget = it },
                                    // 已完成的习惯不参与排序，保持打卡按钮原样
                                    reorderMode = false
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAdd,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("新习惯", style = MaterialTheme.typography.labelLarge)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp, start = 16.dp, end = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = colors.primaryText,
                contentColor = colors.cardBackground,
                actionColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(14.dp)
            )
        }
    }

    actionTarget?.let { habit ->
        HabitActionSheet(
            habit = habit,
            onDismiss = { actionTarget = null },
            onDetail = { actionTarget = null; onOpenDetail(habit.id) },
            onEdit = { actionTarget = null; onLongPress(habit) },
            onFailed = { actionTarget = null; onMarkFailed(habit) },
            onSkip = { actionTarget = null; onSkip(habit) },
            onPause = { actionTarget = null; onLongPress(habit) },
            onArchive = { actionTarget = null; onArchive(habit) },
            onDelete = { actionTarget = null; deleteTarget = habit }
        )
    }

    quickAddTarget?.let { habit ->
        QuickAddDialog(
            habit = habit,
            current = state.rows.firstOrNull { it.habit.id == habit.id }?.record?.accumulatedValue ?: 0.0,
            target = state.rows.firstOrNull { it.habit.id == habit.id }?.targetValue ?: habit.targetValue,
            onDismiss = { quickAddTarget = null },
            onAdd = { v -> onAddValue(habit, v); quickAddTarget = null },
            onOpenDetail = { quickAddTarget = null; onOpenDetail(habit.id) }
        )
    }

    deleteTarget?.let { habit ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${habit.name}」？") },
            text = { Text("会同时删除它的全部打卡记录，无法恢复。若只是暂时不做，可以归档。") },
            confirmButton = {
                TextButton(onClick = { onDelete(habit); deleteTarget = null }) {
                    Text("删除", color = colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun SummaryCard(state: HomeUiState) {
    val colors = LocalXijiColors.current
    XijiCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.selectedDate == state.today) "今天的进度" else "当天完成情况",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.secondaryText
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${state.completed}",
                            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                            color = colors.primaryText
                        )
                        Text(
                            text = " / ${state.total} 项",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.secondaryText,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                val pct = (state.progress * 100).toInt()
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "$pct%",
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            SmoothProgressBar(
                progress = state.progress,
                modifier = Modifier.fillMaxWidth(),
                contentDescription = "完成进度 ${(state.progress * 100).toInt()}%"
            )
            Spacer(Modifier.height(10.dp))
            Text(
                state.encouragement,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText
            )
        }
    }
}

@Composable
private fun WeekStrip(state: HomeUiState, onSelect: (LocalDate) -> Unit) {
    val colors = LocalXijiColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        state.weekDays.forEach { day ->
            val selectedBg = MaterialTheme.colorScheme.primary
            val bg = when {
                day.isSelected -> selectedBg
                day.isToday -> MaterialTheme.colorScheme.primaryContainer
                else -> Color.Transparent
            }
            val fg = when {
                day.isSelected -> MaterialTheme.colorScheme.onPrimary
                day.isToday -> MaterialTheme.colorScheme.primary
                day.isFuture -> colors.secondaryText.copy(alpha = 0.4f)
                else -> colors.primaryText
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !day.isFuture) { onSelect(day.date) }
                    .background(bg)
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    day.dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = if (day.isSelected) 0.85f else 0.7f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${day.dayOfMonth}",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = fg
                )
                Spacer(Modifier.height(5.dp))
                // 完成度指示点：已全部完成实心，部分完成半透明，未开始空心
                val dotColor = if (day.isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                day.scheduled == 0 -> Color.Transparent
                                day.done >= day.scheduled -> dotColor
                                day.done > 0 -> dotColor.copy(alpha = 0.45f)
                                else -> colors.cardBorder
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun FilterRow(state: HomeUiState, onFilter: (HabitFilter) -> Unit) {
    val colors = LocalXijiColors.current
    val reduceMotion = LocalReduceMotion.current
    // 横向滚动：窄屏（360dp）下四个胶囊 + 排序按钮会挤爆，改为可滑动且禁止换行
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HabitFilter.entries.forEach { f ->
            val selected = state.filter == f
            val chipSpec = if (reduceMotion) snap<Color>() else tween<Color>(XijiMotion.FAST)
            val background by animateColorAsState(
                targetValue = if (selected) colors.primaryText else colors.subtleFill,
                animationSpec = chipSpec,
                label = "chipBg"
            )
            val foreground by animateColorAsState(
                targetValue = if (selected) colors.cardBackground else colors.secondaryText,
                animationSpec = chipSpec,
                label = "chipFg"
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(background)
                    .clickable { onFilter(f) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    f.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = foreground,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HabitActionSheet(
    habit: Habit,
    onDismiss: () -> Unit,
    onDetail: () -> Unit,
    onEdit: () -> Unit,
    onFailed: () -> Unit,
    onSkip: () -> Unit,
    onPause: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalXijiColors.current
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(habit.name, style = MaterialTheme.typography.titleLarge, color = colors.primaryText)
            Spacer(Modifier.height(4.dp))
            Text(
                habit.schedule.describe() + " · " + when (habit.type.name) {
                    "QUANTITY" -> "数量型"
                    "QUIT" -> "戒除型"
                    else -> "完成型"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText
            )
            Spacer(Modifier.height(14.dp))
            SheetAction("查看详情与统计", onDetail)
            SheetAction("编辑习惯", onEdit)
            SheetAction("标记今日未达成", onFailed)
            SheetAction("跳过今天（不计入完成率）", onSkip)
            SheetAction("暂停 / 恢复（进入详情操作）", onPause)
            SheetAction("归档（保留历史记录）", onArchive)
            SheetAction("删除习惯", onDelete, danger = true)
        }
    }
}

@Composable
private fun SheetAction(text: String, onClick: () -> Unit, danger: Boolean = false) {
    val colors = LocalXijiColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) colors.danger else colors.primaryText
        )
    }
}

@Composable
private fun QuickAddDialog(
    habit: Habit,
    current: Double,
    target: Double,
    onDismiss: () -> Unit,
    onAdd: (Double) -> Unit,
    onOpenDetail: () -> Unit
) {
    val colors = LocalXijiColors.current
    val unit = habit.targetUnit.ifBlank { "次" }
    val remaining = (target - current).coerceAtLeast(0.0)
    var input by remember(habit.id) { mutableStateOf("") }
    val parsed = input.toDoubleOrNull()
    val quick = listOf(1.0, 5.0, 10.0).filter { it <= maxOf(target, 1.0) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(habit.name) },
        text = {
            Column {
                Text(
                    "已记录 ${com.xiji.habits.domain.model.formatValue(current)}$unit / 目标 ${com.xiji.habits.domain.model.formatValue(target)}$unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { v ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.subtleFill)
                                .clickable { onAdd(v) }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(
                                "+${com.xiji.habits.domain.model.formatValue(v)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.primaryText
                            )
                        }
                    }
                    if (remaining > 0) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { onAdd(remaining) }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(
                                "补满",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() || c == '.' }.take(8) },
                    label = { Text("自定义数量（$unit）") },
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onOpenDetail) { Text("在详情中修改与撤销") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (parsed != null && parsed > 0) onAdd(parsed) },
                enabled = parsed != null && parsed > 0
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = RoundedCornerShape(24.dp)
    )
}


@Composable
private fun CatChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalXijiColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else colors.secondaryText
        )
    }
}

package com.xiji.habits.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xiji.habits.AppContainer
import kotlinx.coroutines.launch
import com.xiji.habits.data.preferences.UserSettings
import com.xiji.habits.ui.backup.BackupScreen
import com.xiji.habits.ui.backup.BackupViewModel
import com.xiji.habits.ui.calendar.CalendarScreen
import com.xiji.habits.ui.calendar.CalendarViewModel
import com.xiji.habits.ui.habits.HabitDetailScreen
import com.xiji.habits.ui.habits.HabitDetailViewModel
import com.xiji.habits.ui.habits.HabitEditScreen
import com.xiji.habits.ui.habits.HabitEditViewModel
import com.xiji.habits.ui.home.HomeViewModel
import com.xiji.habits.ui.home.TodayScreen
import com.xiji.habits.ui.navigation.Routes
import com.xiji.habits.ui.navigation.XijiBottomBar
import com.xiji.habits.ui.settings.SettingsScreen
import com.xiji.habits.ui.settings.SettingsViewModel
import com.xiji.habits.ui.statistics.StatsScreen
import com.xiji.habits.ui.statistics.StatsViewModel

@Composable
fun XijiApp(container: AppContainer, reduceAnimation: Boolean) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in setOf(Routes.TODAY, Routes.CALENDAR, Routes.STATS, Routes.SETTINGS)
    val context = LocalContext.current
    val settings by container.settingsRepository.settings.collectAsState(initial = UserSettings())
    val snackbarHostState = remember { SnackbarHostState() }
    var widgetHelp by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // 启动时恢复提醒（时间/时区变化、重启后由 BootReceiver 处理）
        container.reminderScheduler.rescheduleAll()
    }

    // 从小组件点某个习惯进来时，直接跳到它的详情页
    val activity = context as? android.app.Activity
    LaunchedEffect(activity?.intent?.getLongExtra("habit_id", -1L)) {
        val id = activity?.intent?.getLongExtra("habit_id", -1L) ?: -1L
        if (id > 0) {
            activity?.intent?.removeExtra("habit_id")
            navController.navigate(Routes.habitDetail(id))
        }
    }

    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = Routes.TODAY,
                enterTransition = {
                    if (reduceAnimation) fadeIn(tween(120))
                    else slideInHorizontally(tween(240)) { it / 12 } + fadeIn(tween(200))
                },
                exitTransition = {
                    if (reduceAnimation) fadeOut(tween(100)) else fadeOut(tween(160))
                },
                popEnterTransition = { fadeIn(tween(160)) },
                popExitTransition = {
                    if (reduceAnimation) fadeOut(tween(100))
                    else slideOutHorizontally(tween(220)) { it / 12 } + fadeOut(tween(160))
                }
            ) {
                composable(Routes.TODAY) { entry ->
                    val vm: HomeViewModel = viewModel(
                        factory = XijiViewModelFactory(container),
                        viewModelStoreOwner = entry
                    )
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    TodayScreen(
                        state = state,
                        onSelectDate = vm::selectDate,
                        onToggle = vm::toggle,
                        onOpenDetail = { navController.navigate(Routes.habitDetail(it)) },
                        onLongPress = { navController.navigate(Routes.habitEdit(it.id)) },
                        onAdd = { navController.navigate(Routes.habitEdit()) },
                        onFilter = vm::setFilter,
                        onUndo = vm::undoLast,
                        onConsumeSnack = vm::consumeSnack,
                        onToggleCollapse = vm::toggleDoneCollapsed,
                        snackbarHostState = snackbarHostState,
                        onAddValue = { h, v -> vm.addValue(h, v) },
                        onMarkFailed = vm::markFailed,
                        onSkip = vm::skip,
                        onArchive = { h -> vm.archiveHabit(h.id, true) },
                        onDelete = { h -> vm.deleteHabit(h.id) },
                        onReorder = vm::reorder,
                        onReorderAll = vm::reorderAll,
                        onCategory = vm::setCategory
                    )
                }

                composable(Routes.CALENDAR) { entry ->
                    val vm: CalendarViewModel = viewModel(
                        factory = XijiViewModelFactory(container),
                        viewModelStoreOwner = entry
                    )
                    val state by vm.state.collectAsStateWithLifecycle()
                    CalendarScreen(
                        state = state,
                        onPrev = vm::prevMonth,
                        onNext = vm::nextMonth,
                        onSelectDate = vm::selectDate,
                        onSetStatus = { h, d, s -> vm.setStatus(h, d, s) },
                        onClear = vm::clear,
                        onAddValue = { h, d, v -> vm.addValue(h, d, v) },
                        onOpenHabit = { navController.navigate(Routes.habitDetail(it)) },
                        onToggleBackfillMode = vm::toggleBackfillMode,
                        onBackfill = { h, d -> vm.backfill(h, d) },
                        onBackfillAll = vm::backfillAllMissing
                    )
                }

                composable(Routes.STATS) { entry ->
                    val vm: StatsViewModel = viewModel(
                        factory = XijiViewModelFactory(container),
                        viewModelStoreOwner = entry
                    )
                    val state by vm.state.collectAsStateWithLifecycle()
                    StatsScreen(
                        state = state,
                        onSelectHabit = vm::selectHabit,
                        onSetYear = vm::setYear,
                        onSelectDay = vm::selectDay,
                        onOpenHabit = { navController.navigate(Routes.habitDetail(it)) }
                    )
                }

                composable(Routes.SETTINGS) { entry ->
                    val vm: SettingsViewModel = viewModel(
                        factory = XijiViewModelFactory(container),
                        viewModelStoreOwner = entry
                    )
                    val state by vm.state.collectAsStateWithLifecycle()
                    SettingsScreen(
                        settings = settings,
                        state = state,
                        onThemeMode = vm::setTheme,
                        onAccent = vm::setAccent,
                        onDynamic = vm::setDynamic,
                        onWeekStart = vm::setWeekStart,
                        onReduceAnimation = vm::setReduceAnimation,
                        onOpenBackup = { navController.navigate(Routes.BACKUP) },
                        onOpenCategories = { vm.openCategoryDialog(null) },
                        onOpenWidgetHelp = { widgetHelp = true },
                        onOpenNotificationSettings = {
                            val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            } else {
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                            }
                            runCatching { context.startActivity(intent) }
                        },
                        onOpenExactAlarmSettings = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                            .setData(android.net.Uri.parse("package:${context.packageName}"))
                                    )
                                }
                            }
                        },
                        onSaveCategory = vm::saveCategory,
                        onDeleteCategory = vm::deleteCategory,
                        onEditCategory = { cat ->
                            if (cat.id == -1L) vm.dismissCategoryDialog() else vm.openCategoryDialog(cat)
                        },
                        onConsumeMessage = vm::consumeMessage,
                        onOpenCategoryManager = vm::openCategoryManager,
                        onCloseCategoryManager = vm::closeCategoryManager,
                        onRequestDeleteCategory = vm::requestDeleteCategory,
                        onCancelDeleteCategory = vm::cancelDeleteCategory,
                        onConfirmDeleteCategory = vm::confirmDeleteCategory,
                        onCreateCategory = { vm.openCategoryDialog(null) }
                    )
                }

                composable(
                    route = "${Routes.HABIT_EDIT}?habitId={habitId}",
                    arguments = listOf(navArgument("habitId") { type = NavType.LongType; defaultValue = -1L })
                ) { entry ->
                    val id = entry.arguments?.getLong("habitId") ?: -1L
                    val vm: HabitEditViewModel = viewModel(
                        factory = XijiViewModelFactory(container, if (id > 0) id else 0),
                        viewModelStoreOwner = entry
                    )
                    val state by vm.state.collectAsStateWithLifecycle()
                    HabitEditScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onName = vm::setName,
                        onDescription = vm::setDescription,
                        onIcon = vm::setIcon,
                        onColor = vm::setColor,
                        onCategory = vm::setCategory,
                        onType = vm::setType,
                        onTargetValue = vm::setTargetValue,
                        onTargetUnit = vm::setTargetUnit,
                        onScheduleType = vm::setScheduleType,
                        onToggleWeekday = vm::toggleWeekday,
                        onTimesPerWeek = vm::setTimesPerWeek,
                        onIntervalDays = vm::setIntervalDays,
                        onStartDate = vm::setStartDate,
                        onToggleAdvanced = vm::toggleAdvanced,
                        onConfirmQuitDaily = vm::setConfirmQuitDaily,
                        onAddReminder = vm::addReminder,
                        onUpdateReminder = vm::updateReminder,
                        onToggleReminder = vm::toggleReminder,
                        onRemoveReminder = vm::removeReminder,
                        onSave = vm::save,
                        onSaved = {
                            scope.launch { container.reminderScheduler.rescheduleAll() }
                            navController.popBackStack()
                        }
                    )
                }

                composable(
                    route = Routes.HABIT_DETAIL,
                    arguments = listOf(navArgument("habitId") { type = NavType.LongType })
                ) { entry ->
                    val id = entry.arguments?.getLong("habitId") ?: 0L
                    val vm: HabitDetailViewModel = viewModel(
                        factory = XijiViewModelFactory(container, id),
                        viewModelStoreOwner = entry
                    )
                    val state by vm.state.collectAsStateWithLifecycle()
                    HabitDetailScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(Routes.habitEdit(id)) },
                        onPrevMonth = vm::prevMonth,
                        onNextMonth = vm::nextMonth,
                        onSelectDate = vm::selectDate,
                        onToggleToday = vm::toggleToday,
                        onSetStatus = { d, s -> vm.setStatusOn(d, s) },
                        onClear = vm::clearOn,
                        onAddValue = { d, v -> vm.addValue(d, v) },
                        onUndoValue = vm::undoValue,
                        onSetNote = { d, n -> vm.setNote(d, n) },
                        onTogglePause = vm::togglePause,
                        onArchive = {
                            vm.archive()
                            navController.popBackStack()
                        },
                        onRequestDelete = vm::requestDelete,
                        onCancelDelete = vm::cancelDelete,
                        onConfirmDelete = { vm.confirmDelete { navController.popBackStack() } },
                        onConsumeSnack = vm::consumeSnack
                    )
                }

                composable(Routes.BACKUP) { entry ->
                    val vm: BackupViewModel = viewModel(
                        factory = XijiViewModelFactory(container),
                        viewModelStoreOwner = entry
                    )
                    val state by vm.state.collectAsStateWithLifecycle()
                    BackupScreen(
                        state = state,
                        defaultJsonName = container.backupManager.defaultFileName("json"),
                        defaultCsvName = container.backupManager.defaultFileName("csv"),
                        onBack = { navController.popBackStack() },
                        onExportJson = vm::exportJson,
                        onExportCsv = vm::exportCsv,
                        onPickImport = vm::pickImport,
                        onSetReplace = vm::setReplaceMode,
                        onConfirmImport = {
                            vm.confirmImport {
                                scope.launch { container.reminderScheduler.rescheduleAll() }
                            }
                        },
                        onCancelImport = vm::cancelImport,
                        onConsumeMessage = vm::consumeMessage
                    )
                }
            }
        }

        if (showBottomBar) {
            XijiBottomBar(
                currentRoute = currentRoute,
                onSelect = { route ->
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            popUpTo(Routes.TODAY) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }

    if (widgetHelp) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { widgetHelp = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { widgetHelp = false }) {
                    androidx.compose.material3.Text("知道了")
                }
            },
            title = { androidx.compose.material3.Text("添加桌面小组件") },
            text = {
                androidx.compose.material3.Text(
                    "在桌面长按空白处 → 选择「小组件」→ 找到「习迹」→ 拖入 2×2 进度组件或 4×2 今日列表组件。\n\n" +
                        "小组件直接读取本地数据库，完成状态与 App 内一致，不需要网络。"
                )
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        )
    }
}

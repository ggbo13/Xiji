# 架构说明

## 1. 总体分层

```
UI (Compose)  ──►  ViewModel (StateFlow)  ──►  Repository  ──►  Room DAO  ──►  SQLite
     │                    │
     │                    └──► domain/schedule, domain/statistics（纯 Kotlin，无 Android 依赖）
     └──► theme / components
```

约束：

- 页面不直接访问 DAO，只通过 Repository。
- 业务规则（周期判定、连续、统计、强度）全部是纯函数，放在 `domain/`，可在 JVM 上单测。
- Room 是唯一的持久化事实来源；没有第二份内存缓存作为「完成状态」来源。
- 所有写操作立即落库，UI 通过 Flow 自动刷新。

## 2. 目录结构

```
app/src/main/java/com/xiji/habits/
├── AppContainer.kt            手动依赖容器 + Application
├── MainActivity.kt            单 Activity，edge-to-edge，通知权限请求
├── data/
│   ├── Mappers.kt             Entity ↔ Domain
│   ├── local/
│   │   ├── AppDatabase.kt     Room 数据库（version=1，导出 schema）
│   │   ├── entity/            7 张表
│   │   └── dao/               7 个 DAO
│   ├── preferences/           DataStore（主题、周起始日、语言、动效）
│   └── repository/            HabitRepository（唯一写入口）
├── domain/
│   ├── model/                 Habit / HabitRecord / ScheduleSpec / DayState …
│   ├── schedule/ScheduleEngine.kt      周期判定 + 连续算法
│   └── statistics/StatisticsEngine.kt  完成率 / 趋势 / 热力图 / 强度
├── ui/
│   ├── XijiApp.kt             NavHost + 底部导航
│   ├── ViewModelFactory.kt    显式工厂（未引入 Hilt）
│   ├── theme/                 Color / Type / Theme（含 XijiColors 语义色）
│   ├── components/            XijiCard / CheckmarkButton / Heatmap / MonthCalendar / 图表
│   ├── home/                  TodayScreen + HomeViewModel + HabitCard
│   ├── habits/                HabitEditScreen / HabitDetailScreen / DayEditSheet
│   ├── calendar/              CalendarScreen + ViewModel
│   ├── statistics/            StatsScreen + ViewModel
│   ├── settings/              SettingsScreen + ViewModel
│   └── backup/                BackupScreen + ViewModel
├── reminder/                  ReminderScheduler / ReminderReceiver / BootReceiver
├── backup/                    BackupFile 模型 + BackupManager（导入导出）
└── widget/                    Glance 2×2 / 4×2 + WidgetData
```

## 3. 为什么不用 Hilt

依赖图只有 4 个对象（DB、Repository、Settings、Scheduler），用一个 `AppContainer` 显式构造更易读，
也避免了注解处理带来的构建时间与额外依赖。ViewModel 通过 `XijiViewModelFactory` 创建，
每个导航目的地使用自己的 `viewModelStoreOwner`，因此不同习惯的详情页互不干扰。

## 4. 状态管理

- 每个页面一个 `ViewModel`，对外暴露 `StateFlow<UiState>`。
- `HomeViewModel` 使用 `combine` 把「选中日期 + 筛选 + 习惯 + 记录 + 快照 + 暂停 + 分类 + 设置」
  合并成单一 `HomeUiState`，避免多个独立缓存导致的状态不一致。
- 打卡、撤销、数量追加等操作在 ViewModel 中调用 Repository，然后由 Flow 自动回流刷新。
- 「撤销」通过保存上一个动作的闭包实现，而不是维护一份撤销栈（单步撤销，符合产品需求）。

## 5. 周期与统计的核心抽象

### ScheduleEngine

| 函数 | 职责 |
|---|---|
| `snapshotFor(snapshots, habit, date)` | 取该日期生效的计划快照（历史口径） |
| `isScheduledOn(...)` | 该日是否属于计划 |
| `isPaused(...)` | 是否处于暂停区间 |
| `isCountableDay(...)` | 是否计入分母（计划内 + 已开始 + 非暂停 + 非未来） |
| `dayState(...)` | 该日的展示状态（8 态） |
| `streak(...)` | 当前/最长连续（按天或按周） |

### StatisticsEngine

| 函数 | 职责 |
|---|---|
| `habitStats(...)` | 单习惯：累计、连续、周/月/总完成率、强度 |
| `strength(...)` | 0–100 强度（90 天时间加权） |
| `heatmap(...)` | 区间内每日聚合（用于热力图与趋势） |
| `dayEntries(...)` / `dayRate(...)` | 某日应执行习惯与完成率 |
| `overview(...)` | 跨习惯总览 |

## 6. 线程与性能

- Room 查询在 IO 线程（Room 的 suspend/Flow 自动处理），统计计算在 ViewModel 协程中执行。
- 列表使用 `LazyColumn` + 稳定 key（`habit.id`）。
- 热力图一次查询整段区间后在内存聚合，**不会**按天查库（避免 N+1）。
- 图标使用 emoji 文本，无位图、无网络字体，APK 体积小。
- 统计数据按需计算（页面级），并对区间做上限（最多回溯 10 年 / 365 天窗口）。

## 7. 提醒链路

```
设置提醒 → Repository 写 reminders 表
        → ReminderScheduler.schedule()  每个星期几一个 PendingIntent
        → AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP)
        → ReminderReceiver.onReceive()
             ├─ 已完成 / 暂停 / 已归档 → 不发通知
             ├─ 否则发本地通知（点击进入 App）
             └─ 重新安排下周同一时刻
BootReceiver ← BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED / MY_PACKAGE_REPLACED
             → rescheduleAll()
```

不使用 FCM，不使用 WorkManager 周期任务（最小 15 分钟且不保证准时）。

## 8. 备份事务

`BackupManager.import()` 在 `db.withTransaction { }` 内完成全部写入：
校验失败或任何异常都会回滚，现有数据不受影响。合并模式下 ID 冲突的习惯会重新分配 ID，
并同步重映射 records / reminders / pauses / scheduleHistory / valueEvents 的外键。

## 9. 隐私边界（架构层面）

- 无 `INTERNET` 权限（Manifest 中显式 `tools:node="remove"` 兜底）。
- 无网络库、无统计 SDK、无崩溃上报。
- `allowBackup=false` + `dataExtractionRules` 排除全部域 + `backup_rules.xml`（API ≤ 30）。
- 唯一的数据出口是用户主动触发的 SAF 导出。

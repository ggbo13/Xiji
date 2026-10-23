# 参考项目调研分析

> 调研时间：2026-10-23。信息来自各仓库 README、LICENSE 与部分源码（通过 GitHub 原始文件接口直接读取）。
> 本项目的所有代码均为独立实现，**未复制、未移植任何参考项目的源码**。下文所述「吸收」指设计思路与产品概念，
> 不是代码复用。

## 1. 许可证核查结果

| 项目 | 默认分支 | 许可证（SPDX） | 许可证文件 | 是否可复用代码 |
|---|---|---|---|---|
| Loop Habit Tracker (`iSoron/uhabits`) | `dev` | GPL-3.0 | `LICENSE` 在仓库根目录存在（README 内嵌 GPLv3 全文） | 否。GPLv3 具有传染性，链接/衍生会要求本项目同样以 GPLv3 发布。**未使用任何源码** |
| Table Habit (`FriesI23/mhabit`) | `master` | Apache-2.0 | 有 | 理论上可复用（需保留 NOTICE 与版权声明），但技术栈是 Flutter/Dart，与 Kotlin 原生项目无关。**未使用任何代码** |
| Grit (`shub39/Grit`) | `master` | GPL-3.0 | 有（文件头为 GPLv3 声明） | 否，原因同上。**未使用任何代码** |
| Tsuzuku (`agupta07505/Tsuzuku`) | `main` | MIT | 有 | 可复用（需保留版权与许可声明），但本项目仍选择独立实现，**未复制任何源码** |
| Habits (`shayanmohd/habits-app`) | `main` | MIT | 有 | 同上，**未复制任何源码** |

结论：参考项目仅用于**产品与算法层面的学习**。凡是 GPL-3.0 项目，本项目一律不复制代码，也不复制其特有的算法实现文本。
本项目当前未声明开源许可证，由作者自行决定；如果后续选择开源，不受上述项目许可证约束。

## 2. 逐项调研

### 2.1 Loop Habit Tracker（GPL-3.0，Kotlin，约 10.3k stars）

**观察到的实现（源码级）**

- 数据模型：`Habit` 包含 `frequency: Frequency(numerator, denominator)`，例如「每周 3 次」= `3/7`；
  另有 `NumericalHabitType`（AT_LEAST / AT_MOST）用于数量型习惯。
- 习惯强度：`Score.compute(frequency, previousScore, checkmarkValue)`：
  ```
  multiplier = 0.5 ^ (sqrt(frequency) / 13)
  score = previousScore * multiplier + checkmarkValue * (1 - multiplier)
  ```
  这是指数滑动平均，`frequency` 越高衰减越快，得分收敛到 0..1 区间。
- 连续记录：`Streak(start, end)`，以「天」为单位，`length = start.daysUntil(end) + 1`。
- 历史：`EntryList` 保存 `Entry(value, notes)`，`computedEntries` 由原始记录 + 频率推导。
- 提醒：`Reminder` 模型 + `ReminderScheduler`（`AlarmManager` 系），通知内可直接打卡。
- 小组件：`CheckmarkWidget` / `ScoreWidget` / `HistoryWidget` / `StreakWidget` / `TargetWidget` 等，
  通过 `WidgetUpdater` 在数据变化时刷新。
- 数据可携带性：CSV 与 SQLite 导出；完全离线，无账号。

**优点**：算法经过长期验证；周期表达灵活（分数频率）；小组件体系完整；离线与隐私做得很彻底。

**不足（相对本项目需求）**

- 界面是传统 View 体系，不是 Compose / Material 3。
- 周期用「分数频率」表达，但**不能直接表达「每周一三五」这类指定星期**，也不能表达「每隔 N 天」的锚点语义
  （隔天与每周 3.5 次在数学上不同）。
- 连续记录只按天推进，对「每周 N 次」的习惯会把非计划日也算进连续性，语义不精确。
- 无「戒除型」习惯的独立状态机（只有 YES_NO 与 NUMERICAL 两类）。

**吸收的设计**：习惯强度「不会因一次遗漏清零」的产品理念；通知内直接打卡；多小组件按用途拆分；
CSV 导出便于二次分析。

**重新设计**：本项目用 `ScheduleSpec`（DAILY / WEEKDAYS / TIMES_PER_WEEK / INTERVAL + anchorDate）替代分数频率，
连续算法按「周期」而非「自然日」推进，并显式支持戒除型。

### 2.2 Table Habit（Apache-2.0，Flutter/Dart）

**观察到的实现（README 级）**

- 正向 / 负向习惯分别使用不同的评分模型（"do" 与 "don't"）。
- 习惯分组、拖拽排序、可折叠分组。
- 每个习惯可自定义颜色（内置色板 + 完整取色器）。
- 成长曲线（growth chart）展示长期评分变化。
- 原生 JSON 导入导出，并支持从 Loop Habit Tracker 的 CSV 迁移。
- Material 3 + 动态取色；支持 RTL 与 18+ 语言。
- 有 WebDAV 同步（**本项目明确不引入**）。

**优点**：分组与颜色自定义的交互成熟；导入导出格式考虑迁移；正负向习惯评分分离。

**不足**：Flutter 技术栈，无法直接用于原生 Android；WebDAV 同步与「绝不联网」的目标冲突；体量较大。

**吸收的设计**：正向/负向习惯分开建模；分类 + 拖拽排序 + 可折叠分组；JSON 备份带版本号；
颜色自定义。

**重新设计**：戒除型不采用「负向评分」，而是用「达成 / 破戒 / 未记录 / 跳过」四态显式建模，
未记录**不自动视为成功**。

### 2.3 Grit（GPL-3.0，Kotlin Multiplatform + Compose）

**观察到的实现（源码级）**

- `Habit(id, title, description, time, days: Set<DayOfWeek>, index, reminder, targetValue, targetUnit, categoryId)`：
  用 `Set<DayOfWeek>` 表达指定星期，`targetValue > 0` 即视为可量化习惯。
- `HabitStatus(id, habitId, date: LocalDate, value)`：打卡记录以「日期 + 数值」为单位。
- 数据层使用 `HabitRepo` 接口 + Room/SQLDelight；设置用 DataStore 接口抽象。
- 提醒：`NotificationAlarmScheduler` + `BootReceiver` + `IntentReceiver`。
- 组件：`AllTasksWidget` / `HabitOverviewWidget` / `HabitStreakWidget` / `HabitWeekChartWidget`（Glance）。
- 支持生物识别锁、备份导出/恢复。

**优点**：Compose 结构清晰；指定星期的表达直观；Glance 组件拆分细致；通知与开机恢复处理完整。

**不足**：核心是「待办 + 习惯」混合体，任务系统较重；GPL-3.0 不可复用代码；
部分能力（订阅、分析）与本项目无关。

**吸收的设计**：指定星期用位掩码；提醒的调度/恢复链路（AlarmManager + BootReceiver + 包更新）；
组件按「概览 / 列表 / 连续」拆分。

**重新设计**：不引入待办任务系统；不引入订阅与分析；所有提醒逻辑自行实现并加入「已完成则不提醒」的判断。

### 2.4 Tsuzuku（MIT，Kotlin + Compose）

**观察到的实现（源码级）**

- `StreakCalculator.calculate(logs, creationTime)`：按日期排序后计算当前/最长连续、总完成次数、完成率。
  当前连续只有在「最后完成日是今天或昨天」时才继续，否则归零。
- 数据：`Habit` / `HabitLog(isCompleted, date)` + Room DAO + Repository。
- 界面：年度热力图 `StreakHeatmap`、习惯日历、Insights 概览。
- 提醒：`HabitNotificationHelper` + `ReminderReceiver`，处理 Android 13 通知权限。
- 备份：Settings 内的 JSON 导入导出。
- 额外：专注计时器、极简桌面启动器（**本项目不需要**）。

**优点**：结构简单易懂；热力图实现轻量；提醒链路完整；MIT 许可友好。

**不足**：连续算法只支持「每日」语义，无法处理每周 N 次或指定星期；完成率把未记录日直接算失败；
日期用字符串比较，跨时区/夏令时没有显式处理；无数量型习惯。

**吸收的设计**：热力图与日历的视觉组织；JSON 备份入口放在设置页；通知权限的兼容处理方式。

**重新设计**：连续与完成率全部重写为「有效计划日」语义；日期统一用 `LocalDate` + epochDay，
不用字符串比较；增加数量型与戒除型。

### 2.5 Habits（MIT，Kotlin + Compose + Room + Glance）

**观察到的实现（源码级）**

- `HabitEntity` + `DailyRecordEntity` + `HabitDao` + `DailyRecordDao` + `HabitRepository`，MVVM + StateFlow。
- `FrequencyEvaluator.isRequiredOn(habit, date)`：支持 DAILY / WEEKLY（位掩码）/ INTERVAL（相对 startDate 取模）。
- `HabitSummaryWidget` + `HabitsWidgetReceiver`（Glance）。
- 多语言字符串资源（含 zh）。
- 项目结构：`data/` `domain/` `ui/screens/` `ui/theme/` `widget/`，`app/schemas/` 导出 Room schema。

**优点**：工程结构清晰，规模适中；频率判定逻辑简洁；Glance 组件示例完整；有 schema 导出。

**不足**：功能较浅（无数量型、无统计页、无备份、无提醒细节）；连续与统计逻辑简单；
测试覆盖很少。

**吸收的设计**：单 module 的目录分层；`FrequencyEvaluator` 式的纯函数周期判定（本项目扩展为 `ScheduleEngine`）；
Room schema 导出。

**重新设计**：在同样的分层上补齐数量型、戒除型、计划快照（历史口径）、备份事务、统计引擎与测试。

## 3. 功能吸收 / 重新设计对照表

| 能力 | 参考来源 | 本项目处理 |
|---|---|---|
| 灵活周期 | Loop（分数频率）、Habits（位掩码/间隔） | 重新设计为 `ScheduleSpec` 四态 + 锚点日期，可单测 |
| 连续打卡 | Loop（按天）、Tsuzuku（按天） | 重新设计：按周期推进，每周 N 次按自然周结算，未结束周不判负 |
| 习惯强度 | Loop（EMA） | 重新设计：90 天时间加权窗口，公式与参数在 `StatisticsEngine` 中公开注释 |
| 正/负向习惯 | Table Habit | 重新设计为三类型（完成/数量/戒除）+ 四态记录 |
| 分组与排序 | Table Habit、Grit | 分类 + 长按菜单 + 排序模式拖拽 |
| 颜色/图标 | Table Habit、Grit | 内置色板 + emoji 图标（无网络字体） |
| 热力图 | Tsuzuku、Loop | Compose 自绘，区分「无计划 / 未达标 / 部分 / 完成」 |
| 提醒 | Loop、Grit、Tsuzuku | AlarmManager 精确闹钟 + 开机/时区/包更新恢复 + 已完成不提醒 |
| 小组件 | Loop、Grit、Habits | Glance 2×2 进度 + 4×2 列表，快捷打卡直接写 Room |
| 备份 | Table Habit（JSON）、Loop（CSV） | JSON（带 schemaVersion）+ CSV；SAF；事务导入 + 回滚 |
| 隐私 | Loop（离线） | 不申请 INTERNET；allowBackup=false + dataExtractionRules；无任何 SDK |

## 4. 明确未获取 / 未确认的内容

- 各项目**没有**逐一阅读全部源码，仅阅读了 README、LICENSE 以及与上述结论直接相关的模型/算法文件。
  因此不对其内部实现细节做超出上述范围的断言。
- Loop 的 `Score` 常数（`13`、`0.5`）来自源码，但其调参依据仓库中未见文档说明，本项目未采用该公式。
- Table Habit 的「正负向评分模型」具体公式未阅读源码，仅有 README 描述，因此本项目未参考其公式。
- 各项目的 UI 截图未做像素级比对，视觉设计为本项目独立完成。

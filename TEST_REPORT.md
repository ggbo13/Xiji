# 测试与构建报告

> 本文件保留初始版本的验证记录。本次流畅度优化已通过 121 项测试，最新构建与验证说明见 [性能优化记录](PERFORMANCE_REPORT.md)。

> 生成时间：2026-10-23
> 环境：Linux（2 vCPU / 3.9 GB RAM），JDK 17.0.13（Temurin），Gradle 8.11.1，AGP 8.7.3，
> Android SDK Platform 35 / Build-Tools 35.0.0。
> **所有结果均为实际执行所得，未做任何推测或填充。**

## 1. 构建结果

| 目标 | 命令 | 结果 | 产物 |
|---|---|---|---|
| Debug APK | `./gradlew :app:assembleDebug` | **BUILD SUCCESSFUL** | `app-debug.apk`，19.9 MB |
| Release APK | `./gradlew :app:assembleRelease` | **BUILD SUCCESSFUL**（2m31s） | `app-release-unsigned.apk`，13.0 MB |
| 单元测试 | `./gradlew :app:testDebugUnitTest` | **BUILD SUCCESSFUL** | 119 tests / 0 failures |
| Room schema 导出 | 构建时由 KSP 自动执行 | 成功 | `app/schemas/com.xiji.habits.data.local.AppDatabase/1.json` |

APK 元信息（`aapt2 dump badging`）：

```
package: name='com.xiji.habits.debug' versionCode='5' versionName='1.4.0-debug'
minSdkVersion:'26'
targetSdkVersion:'35'
```

## 2. 单元测试明细（119 项，全部通过）

| 测试类 | 数量 | 覆盖内容 |
|---|---|---|
| `ScheduleEngineTest` | 14 | 每日连续、今天未完成不中断、断档、周一三五不因周二中断、每周 N 次按周结算、当前周不提前判负、隔天习惯、暂停排除、未来日期、戒除型未记录、非计划日、闰年 2/29、跨年、跳过不打断 |
| `StatisticsEngineTest` | 9 | 完成率排除非计划日、跳过不进分母、数量型同日只算一次、戒除型未记录算未完成、强度衰减、无记录强度为 0、每周配额、暂停排除、热力图区分有无计划 |
| `ExtraLogicTest` | 11 | 计划快照保留历史、数量型部分/达标、重复记录去重、撤销后统计下降、周连续断档、时区无关日期运算、系统日期回拨、强度小幅下降、长期不活跃归零、暂停不计入日完成率、周期文案 |
| `ReminderTriggerTest` | 4 | 同日未到点、同日已过点顺延一周、同周稍后星期、严格晚于当前时间 |
| `RepositoryPersistenceTest` | 11 | 真实 Room：新建并持久化（模拟重开）、重复打卡不产生重复记录、撤销、数量累加到达标、精确撤销最后一次、输入上限截断、归档保留历史、暂停与恢复、周期变更写快照、排序持久化、删除级联 |
| `BackupRoundTripTest` | 6 | 导出→清空→导入完全一致、合并导入重新分配 ID 并重映射外键、非法关联被拒绝且不破坏数据、未来版本被拒绝、错误日期格式被拒绝、预览数量正确 |
| `BackfillTest` | 10 | 补签标记写入、今天不算补签、普通历史编辑不标记、改状态后标记保留、补签计入统计、数量型补签、今日数量不标记、撤销补签、补签保留备注、未来日期不被标记 |
| `MigrationTest` | 2 | v1→v2 迁移保留习惯与记录、新增 `isBackfilled` 列且旧数据默认 0、唯一索引不丢失 |
| `ScreenshotTest` / `2` / `3` | 9 | Robolectric 原生图形渲染：今日（浅/深）、日历、统计、设置（深）、新建习惯、详情、空状态、360dp 窄屏 + 超长名称 |
| `BackfillScreenshotTest` | 3 | 补签模式的日历页渲染；补签面板文案（「记录 3 天前的情况」「补签备注」）语义断言；今天的面板不出现补签文案 |
| `IconRenderTest` | 1 | 用真实资源渲染自适应图标前景/背景、圆形遮罩与 legacy PNG |
| `WidgetSyncTest` | 10 | 新建/编辑/删除/打卡/撤销/暂停/恢复/数量追加/排序/导入 全部触发小组件刷新 |
| `ListStatsTest` | 4 | 列表页轻量统计与完整统计在连续/周配额上结果一致（含暂停、空数据） |
| `WidgetSizeTest` | 3 | 行数随高度增长且不为负；三种习惯类型的操作文案正确 |
| `CategoryManageTest` | 2 | 删除分类保留习惯与记录；删除动作触发组件刷新 |
| `WidgetLayoutTest` | 6 | 进度换算边界、行数上限、日期渲染 |
| `WidgetRenderTest` | 1 | 深浅色配色的对比度方向正确 |
| `WidgetPreviewTest` | 4 | 按 1:1 布局参数渲染 2×2 / 4×2 的浅色与深色预览 |
| `ReorderLogicTest` | 7 | 待完成顺序回填不移动已完成槽位、尺寸不匹配时拒绝、排序模式下不被仓库顺序覆盖、归档/新增后的合并、本地顺序映射、TalkBack 上移/下移与越界保护 |
| `ReorderInteractionTest` | 2 | Robolectric 真实触摸：进入排序模式后每张待完成卡片都有把手（已完成行没有）；按住把手向下拖一行后提交的顺序为 [2,1,3,4]，并导出排序模式截图 |

### 需求映射（关键测试场景）

| 需求条目 | 对应测试 |
|---|---|
| 重复点击不会产生重复记录 | `repeated check in does not duplicate records`（连续 5 次 → 1 条） |
| 撤销打卡后统计正确 | `undo removes record and stats`、`undo reduces stats` |
| 数量型正确累计目标 | `quantity accumulates and marks done at target` |
| 数量记录不超过合理范围 | `value input is clamped`（上限 1,000,000） |
| 戒除型区分未记录和成功 | `quit habit unrecorded is pending not done`、`quit habit unrecorded counts as miss` |
| 修改历史记录后统计同步 | `undo reduces stats`（Flow 驱动，UI 层自动刷新） |
| 每周三次连续周期 | `times per week uses weekly quota and current week is not failed early` |
| 周一三五非计划日 | `mon wed fri habit does not break on tuesday` |
| 跨月 / 跨年 / 闰年 | `cross year streak`、`leap year feb 29 handled` |
| 时区变化 | `timezone independent date math`（LocalDate 语义，无毫秒除法） |
| 修改系统日期 | `system date moved backwards does not create future completions` |
| 暂停后恢复 | `pause and resume`、`paused period excluded from stats` |
| 中途更改目标规则 | `schedule change writes snapshot without touching history`、`schedule snapshot keeps history when goal changes` |
| 未结束周期不提前判定失败 | `times per week uses weekly quota and current week is not failed early` |
| 强制关闭 / 重启后数据仍在 | `create and check in persists`（Room 落盘，非内存列表） |
| 导出后重新导入一致 | `export then import replace restores everything` |
| 非法备份不破坏数据 | `invalid backup is rejected without touching data` |
| 归档不删除历史 | `archive keeps history` |
| 删除级联 | `delete cascades records` |
| 拖拽排序不能只靠动画「看起来对」 | `draggingFirstCardDownSwapsItWithTheNext`（真实指针事件 → 提交顺序断言） |
| 排序不能把已完成的行挪走 | `splice keeps done habits in their slots` |
| 拖拽排序对 TalkBack 可用 | `move reorders without dragging and commits` |

## 3. 隐私验证（实际检查构建产物）

```bash
$ANDROID_HOME/build-tools/35.0.0/aapt2 dump permissions app-debug.apk
```

输出（完整）：

```
package: com.xiji.habits.debug
uses-permission: name='android.permission.POST_NOTIFICATIONS'
uses-permission: name='android.permission.RECEIVE_BOOT_COMPLETED'
uses-permission: name='android.permission.SCHEDULE_EXACT_ALARM'
uses-permission: name='android.permission.USE_EXACT_ALARM'
uses-permission: name='android.permission.VIBRATE'
uses-permission: name='android.permission.WAKE_LOCK'
uses-permission: name='android.permission.FOREGROUND_SERVICE'
permission: com.xiji.habits.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
uses-permission: name='com.xiji.habits.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

结论：

- **不存在 `android.permission.INTERNET`**，也不存在 `ACCESS_NETWORK_STATE`。
- `WAKE_LOCK` / `FOREGROUND_SERVICE` 来自 Glance 传递依赖的 `androidx.work:work-runtime:2.7.1`。
  这是 Gradle 依赖树中确认的硬依赖（`glance:1.1.1 → work-runtime:2.7.1`），无法通过 `exclude` 移除。
  应用代码不调用 WorkManager，也不会创建其数据库，但权限会出现在 APK 中。此项已记入 README 的已知限制。
- 合并后的 Manifest 中 `allowBackup="false"`、`dataExtractionRules`、`fullBackupContent="false"`、
  `usesCleartextTraffic="false"`、`networkSecurityConfig` 均已生效（见
  `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`）。

## 4. 界面验证（Robolectric 原生渲染）

用 Robolectric `GraphicsMode.NATIVE` 真实渲染 Compose 界面并导出 PNG，确认页面能完成布局、不崩溃、
在深色与窄屏下表现正常。截图位于 `docs/screenshots/`：

| 文件 | 内容 | 观察 |
|---|---|---|
| `today-light.png` | 今日（浅色） | 摘要卡、七日条、筛选、待完成/已完成分组、FAB 层级清晰 |
| `today-dark.png` | 今日（深色） | 对比度正常，卡片描边可见，强调色切换为亮绿 |
| `calendar.png` | 日历 | 月历 5 种状态标记可辨（实心/半透明/空心/短横/灰点），图例正确 |
| `stats.png` | 统计 | 总览、7 天柱状、30 天折线、热力图与图例渲染正常 |
| `settings.png` | 设置（深色） | 主题分段、强调色、开关、分组行对齐 |
| `edit.png` | 新建习惯 | 分组卡片、图标网格、类型选择、目标输入布局正常 |
| `detail.png` | 习惯详情 | 统计块、强度条、月历、选中日期卡片、热力图入口 |
| `empty.png` | 空状态 | 文案与入口居中，不空洞 |
| `narrow.png` | 360dp 窄屏 + 超长名称 | 名称正确省略为「每天早上起床后先喝一杯…」，未挤压打卡按钮 |
| `reorder-mode.png` | 排序模式 | 待完成行右侧为拖拽把手（已完成行仍是打卡按钮），顶部出现操作提示条，排序按钮切换为「完成」 |
| `reorder-after-drag.png` | 拖动一格之后 | 「跑步」与「阅读」互换，「写日记」仍在已完成分组，顺序已提交 |

**这些截图验证的是渲染正确性，不等于真机体验验证**（触摸、手势、输入法、系统栏适配仍需真机确认）。

## 5. 未执行的验证（明确说明）

| 项目 | 原因 |
|---|---|
| 真机 / 模拟器安装运行 | 本环境无 `/dev/kvm`，无法启动 Android 模拟器 |
| 仪器化测试（`connectedAndroidTest`） | 同上 |
| 通知实际弹出、精确闹钟授权流程 | 需要真机系统权限交互 |
| 小组件实际添加到桌面 | 需要真机 Launcher |
| 冷启动耗时、滚动帧率 | 需要真机测量 |
| 拖拽手感（跟手度、震动强度） | 需要用手指在真机上拖；自动化只能验证顺序结果，无法判断手感 |
| 100 习惯 / 2 年历史的性能压测 | 未执行；代码层面已避免 N+1（热力图区间查询一次完成） |

以上项目在交付说明中列为「待真机验证」，未在报告中伪造结果。

## 6. 复现命令

```bash
# 环境
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 构建
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease

# 测试（含 Room / 备份 / 截图）
./gradlew :app:testDebugUnitTest

# 隐私检查
$ANDROID_HOME/build-tools/35.0.0/aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk
```

# 习迹流畅度优化记录

日期：2026-10-23

针对所有页面滚动、习惯拖动排序卡顿完成以下修改：

| 场景 | 改动及作用 |
| --- | --- |
| 拖动排序 | 只有正在拖动或回弹的卡片应用手势位移，修复整列卡片一起晃动的问题。 |
| 拖动到列表边缘 | 使用屏幕帧时钟驱动自动滚动，根据帧间隔换算速度，并在自动滚动时继续检查换位。 |
| 排序抬起动画 | 在绘制图层更新阴影，减少逐帧重组。 |
| 数据刷新 | 首页、日历、统计、详情页的计算通过 `flowOn(Dispatchers.Default)` 在后台执行，主线程负责更新界面状态。 |
| 日历 | 一次按日期建立记录索引，每天只处理当天记录，复用选中日详情结果。 |
| 统计 | 按习惯分组历史记录和暂停区间，复用各习惯的统计结果，避免总览与列表重复计算。 |
| 详情 | 通过数据库只订阅当前习惯的打卡记录。 |
| 进度条 | 动画仅更新绘制，避免逐帧改变布局宽度；遵循减少动画设置。 |
| 列表 | 首页、日历、统计的重复卡片设置 `contentType`，供滚动时复用。 |

## 验证结果

- 原有 119 项测试通过。
- 新增两项回归测试先在原实现上失败，再在修复后通过：
  - 真实 Compose 手势检查拖动和松手回弹期间，邻近卡片保持原位。
  - 统计测试检查历史记录的访问次数，防止重新出现每个习惯扫描全表的问题。
- 全量结果：121 项测试，0 失败、0 错误。
- Android Lint：0 错误，24 个警告、1 条提示。提示涉及依赖版本、目标 API、资源和已有写法等，未扩大本次修改范围。
- 首页、暗色模式、窄屏、日历、统计、详情和排序等页面通过 Robolectric 渲染测试。

固定样本为 20 个习惯、600 条历史记录，调用统计总览时，对原始记录列表的读取次数从 **37,200 次降到 1,800 次**，减少约 **95.2%**。该指标衡量重复遍历，不代表整机帧率或耗时提升比例。

当前环境没有连接 Android 真机，尚未测量设备上的滚动帧率、掉帧比例及 60/90/120Hz 下的实际手感。

## 测试版安装

测试版包名为 `com.xiji.habits.smooth`，版本为 `1.4.0-smooth`，采用 release 配置并关闭调试模式，使用本机调试证书签名。

源码附带 APK 与本机签名不同，因此提供独立包名的共存测试版，安装后保留原应用和数据。测试版拥有独立数据；需要体验现有习惯时，可先在原应用“设置 → 备份”导出，再在测试版导入。

正式覆盖升级需要原应用的签名私钥。现有 `debug`、`release` 构建配置及正式包名保持原样，新增 `smooth` 构建类型便于独立验证。

## 复现命令

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:assembleSmooth
```

主要依据：[Compose 性能最佳实践](https://developer.android.com/develop/ui/compose/performance/bestpractices)、[Compose 图形修饰符](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)、[Kotlin Flow 的 flowOn](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/flow-on.html)。

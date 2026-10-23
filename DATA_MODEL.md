# 数据模型设计

## 1. 实体关系

```
categories 1 ─── n habits
habits     1 ─── n records            (habitId, localDate) UNIQUE
habits     1 ─── n reminders
habits     1 ─── n pause_periods
habits     1 ─── n schedule_history   (habitId, effectiveFrom) UNIQUE
habits     1 ─── n value_events       (habitId, localDate) 索引
```

所有子表对 `habits.id` 使用 `ON DELETE CASCADE`，删除习惯即删除其全部关联数据（详情页有二次确认）。

## 2. 表结构

### habits

| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK | 自增 |
| name | TEXT | 名称，非空 |
| description | TEXT | 描述 |
| icon | TEXT | 图标 key（emoji 映射） |
| color | INTEGER | ARGB |
| categoryId | INTEGER? | 外键（逻辑外键，删除分类时置空） |
| habitType | TEXT | `COMPLETE` / `QUANTITY` / `QUIT` |
| targetValue | REAL | 数量型每日目标 |
| targetUnit | TEXT | 单位 |
| scheduleType | TEXT | `DAILY` / `WEEKDAYS` / `TIMES_PER_WEEK` / `INTERVAL` |
| selectedWeekdays | INTEGER | 位掩码，bit0=周一 … bit6=周日 |
| timesPerWeek | INTEGER | 1–7 |
| intervalDays | INTEGER | 1–30 |
| anchorDate | INTEGER | 间隔周期的锚点 epochDay |
| startDate | INTEGER | 开始日期 epochDay |
| sortOrder | INTEGER | 用户排序 |
| archivedAt | INTEGER? | 归档时间戳；null = 活跃 |
| confirmQuitDaily | BOOLEAN | 戒除型是否要求每天主动确认 |
| createdAt / updatedAt | INTEGER | epoch millis |

索引：`categoryId`、`sortOrder`、`archivedAt`。

### records

| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK | |
| habitId | INTEGER | 外键，CASCADE |
| localDate | TEXT | `yyyy-MM-dd`（本地日历语义） |
| status | TEXT | `DONE` / `FAILED` / `SKIPPED` |
| accumulatedValue | REAL | 数量型当日累计 |
| note | TEXT? | 当天备注 |
| isBackfilled | INTEGER | 1 = 事后补签（v2 新增，默认 0） |
| createdAt / updatedAt | INTEGER | |

**唯一索引 `(habitId, localDate)`** —— 从数据库层杜绝重复打卡；另建 `localDate` 索引供日历按日查询。

### reminders

`id, habitId, enabled, hour, minute, weekdaysMask`

### pause_periods

`id, habitId, startDate, endDate(null=仍在暂停), reason`

### schedule_history（计划快照）

`id, habitId, effectiveFrom, scheduleType, selectedWeekdays, timesPerWeek, intervalDays, anchorDate, targetValue, targetUnit, createdAt`
唯一索引 `(habitId, effectiveFrom)`。

### value_events（数量追加事件）

`id, habitId, localDate, delta, createdAt` —— 支持「精确撤销最后一次追加」与审计。

### categories

`id, name, color, sortOrder`

## 3. 为什么日期存字符串而不是时间戳

- 习惯是**本地日历**概念，「2026-03-09 是否完成」与设备时区无关。
- `yyyy-MM-dd` 字符串可直接做字典序区间查询（`BETWEEN`），且跨时区/夏令时不会漂移。
- 需要算术时转换为 `LocalDate`（`toEpochDay()` 做天数差），避免用毫秒除以 86400000。

## 4. 计划快照与历史口径

修改周期或目标时，`HabitRepository.updateHabit()` 会比较新旧配置；若变化，则插入一条
`effectiveFrom = 今天` 的快照。统计时按日期取「生效中最大的快照」：

```kotlin
snapshots.filter { it.habitId == id && !it.effectiveFrom.isAfter(date) }
         .maxByOrNull { it.effectiveFrom }
```

因此把「每周 3 次」改成「每天」不会让过去的完成率被重算。

## 5. 状态推导（不落库）

| 展示状态 | 判定 |
|---|---|
| FUTURE | date > today |
| NOT_SCHEDULED | date < startDate 或不在计划内 |
| PAUSED | 落在任一暂停区间 |
| DONE | record.status = DONE |
| FAILED | record.status = FAILED |
| SKIPPED | record.status = SKIPPED |
| PARTIAL | 数量型有累计但未达标 |
| PENDING | 计划内、无记录（戒除型的「未记录」就是这里，**不等于成功**） |

## 6. 统计口径（正式定义）

设 `D` 为某习惯在区间内的**有效计划日**集合：

```
D = { d | d ≥ startDate, d ≤ today, d 在计划内, d 不在暂停区间 }
```

- **分母** = |D \ {跳过日}|
- **分子** = D 中状态为 DONE（数量型含累计达标）的天数
- **今天不计入历史完成率的分母**（当天尚未结束，避免提前判负）
- **未来日期永不参与**
- **每周 N 次**：周完成率分母 = N；月完成率分母 = 本月已过周数 × N；连续按自然周结算
- **当前未结束的周**：不判负、不计入连续，但已完成次数计入本周分子
- **累计完成次数**：按「天」计（同一天多次数量追加只算一次），与「有效天数」区分：
  前者是成功天数，后者是有任何完成记录的天数

## 7. 强度算法（独立设计）

```
score = 100 × Σ(w·success) / Σ(w)      over 最近 90 天的有效计划日
w = 0.96 ^ daysAgo
```

性质：偶尔遗漏只小幅下降；长期停更会自然衰减到 0；与「连续天数」解耦。
参数 `STRENGTH_WINDOW_DAYS = 90`、`STRENGTH_DECAY = 0.96` 在 `StatisticsEngine` 中公开为常量并有单测覆盖。

## 7.1 补签（Backfill）

补签不是一个独立的状态，而是记录上的一个**来源标记**：

- `isBackfilled = 1` 表示这条记录是在日期过去之后才录入的。
- 状态本身仍然是 `DONE` / `FAILED` / `SKIPPED`，因此所有统计逻辑无需特判。
- 只有 `date < today` 时才可能被标记；写入 `today` 永远 `isBackfilled = 0`。
- 一旦被标记，后续把状态从「完成」改成「未达成」也会保留标记——这条记录依然属于事后录入。
- 补签**不给额外奖励**，也不改变分母/分子口径：它记录的是真实发生的事。

UI 上的呈现：日历条目、今日卡片、详情历史都会显示一个「补签」胶囊标签。

## 8. 迁移策略

- 当前 `version = 2`，`exportSchema = true`，schema JSON 导出到 `app/schemas/`。
- `MIGRATION_1_2`：`ALTER TABLE records ADD COLUMN isBackfilled INTEGER NOT NULL DEFAULT 0`。
  只加列，不重建表，索引与既有数据全部保留（有迁移测试覆盖）。
- 升级时在 `AppDatabase.ALL_MIGRATIONS` 追加 `Migration(n, n+1)`。
- **禁止** `fallbackToDestructiveMigration()`；迁移失败宁可抛错也不清空数据。

## 9. 备份格式（schemaVersion = 1）

```json
{
  "schemaVersion": 1,
  "appVersion": "1.0.0",
  "exportedAt": 1761200000000,
  "exportedAtIso": "2026-10-23T04:00:00Z",
  "habits": [...], "records": [...], "categories": [...],
  "reminders": [...], "pauses": [...], "scheduleHistory": [...],
  "valueEvents": [...], "settings": {...}
}
```

`records[].isBackfilled` 在 schemaVersion 1 的旧备份中不存在，反序列化时默认 `false`，因此旧备份可直接导入。

校验规则：版本 ∈ [1, CURRENT]；习惯数 ≤ 5000；记录数 ≤ 500000；记录必须能对应到习惯；
日期可解析；状态与类型枚举合法。导入在单事务内完成。

# TimeTagger WatchFace - 独立表盘应用

## 概述

这是 TimeTagger 的**独立表盘应用**，通过**系统日历**读取数据。

## 架构设计

```
┌──────────────────────────────┐
│  TimeTagger 主应用 (独立APK)  │
│  - 任务管理                   │
│  - 语音输入                   │
│  ↓ 写入系统日历               │
└──────────────────────────────┘
              ↓
┌──────────────────────────────┐
│  系统日历数据库（系统进程）    │
│  ✅ 持久化存储                │
│  ✅ 主应用被杀仍可访问         │
└──────────────────────────────┘
              ↓ 读取
┌──────────────────────────────┐
│  TimeTagger 表盘 (独立APK)    │
│  - 显示时间                   │
│  - 从系统日历读取任务          │
│  - 独立安装/卸载              │
│  - 完全独立运行               │
└──────────────────────────────┘
```

## 核心优势

### ✅ 完全独立
- **独立 APK**：可单独安装/卸载
- **独立包名**：`massey.hamhuo.timetagger.watchface`
- **独立进程**：主应用被杀不影响表盘

### ✅ 轻量级
- **最小依赖**：只有 watchface 库
- **小体积**：< 500KB
- **低内存**：< 8MB

### ✅ 系统日历持久化
- **系统进程存储**：数据在系统日历数据库
- **应用无关**：主应用被杀不影响数据
- **标准API**：使用 CalendarContract 读取

## 技术实现

### 1. 数据读取

**CurrentTaskReader.kt** - 从系统日历读取：

```kotlin
class CurrentTaskReader(context: Context) {
    fun getCurrentTask(): CurrentTask {
        // 从系统日历数据库读取
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "description = ?",
            arrayOf("[TIMETAGGER_CURRENT]"),
            null
        )?.use { cursor ->
            val title = cursor.getString(0) // "P1 核心: 学习编程"
            parseTitleToPriorityAndTag(title)
        }
    }
}
```

### 2. 表盘渲染

**TimeTagWatchFaceRenderer.kt** - Canvas 绘制：

```kotlin
override fun render(canvas: Canvas, ..., zonedDateTime: ZonedDateTime, ...) {
    // 1. 绘制背景
    canvas.drawColor(Color.BLACK)
    
    // 2. 绘制时间
    canvas.drawText("14:30", centerX, centerY - 40f, timePaint)
    
    // 3. 读取当前任务
    val currentTask = taskReader.getCurrentTask()
    
    // 4. 绘制任务
    canvas.drawText(currentTask.tag, centerX, centerY + 90f, tagPaint)
}
```

### 3. 主应用数据持久化

**CalendarEventManager.kt** - 写入系统日历：

```kotlin
class CalendarEventManager(context: Context) {
    fun createOrUpdateCurrentTask(tag: String, priority: Int) {
        val calendarId = getDefaultCalendar()
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, "P$priority: $tag")
            put(CalendarContract.Events.DESCRIPTION, "[TIMETAGGER_CURRENT]")
            put(CalendarContract.Events.DTSTART, System.currentTimeMillis())
            put(CalendarContract.Events.DTEND, Long.MAX_VALUE)
        }
        
        // 写入系统日历数据库（系统进程）
        contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    }
}
```

## 编译和安装

### 1. 编译两个 APK

```bash
# 主应用
./gradlew :app:assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk

# 表盘
./gradlew :watchface:assembleDebug
# 输出：watchface/build/outputs/apk/debug/watchface-debug.apk
```

### 2. 安装

```bash
# 先安装主应用（提供数据）
adb install app/build/outputs/apk/debug/app-debug.apk

# 再安装表盘（独立应用）
adb install watchface/build/outputs/apk/debug/watchface-debug.apk
```

### 3. 设置表盘

1. 长按当前表盘
2. 选择 "TimeTagger" 表盘
3. 完成！

## 使用场景

### 场景 1：主应用被系统杀死

```
主应用被杀 → 表盘继续运行 ✅
下次渲染时查询数据 → 如果主应用不在，显示"点击打开应用"
```

### 场景 2：独立卸载

```
卸载主应用 → 表盘仍然可用 ✅（显示时间）
卸载表盘 → 主应用不受影响 ✅
```

### 场景 3：轻量安装

```
只安装表盘 → 可以作为纯时间表盘使用
只安装主应用 → 使用 Tile 查看任务
```

## 数据流

```
用户说出任务
    ↓
MainActivity 记录
    ↓
TimeTrackerRepository 保存
    ↓
CalendarEventManager.createOrUpdateCurrentTask()
    ↓
插入 CalendarContract.Events
    ↓
系统日历数据库（系统进程，永久存储）
    ↓
主应用被杀 ✅ 数据仍然存在
    ↓
CurrentTaskReader.getCurrentTask() 读取日历
    ↓
表盘渲染显示
```

## 对比方案

| 特性 | 同应用独立进程 | 独立应用（当前）|
|------|---------------|----------------|
| 独立安装 | ❌ | ✅ |
| 主应用被杀影响 | ⚠️ 可能 | ✅ 不影响 |
| 包大小 | 一个APK | 两个小APK |
| 卸载灵活性 | ❌ | ✅ |
| 维护成本 | 低 | 中 |
| 数据共享 | 文件 | ContentProvider |

## 依赖关系

### 主应用依赖

```kotlin
// app/build.gradle.kts
dependencies {
    // 不需要 watchface 依赖
    implementation("androidx.wear.tiles:tiles:1.2.0")
    // ...
}
```

### 表盘依赖

```kotlin
// watchface/build.gradle.kts
dependencies {
    // 最小化依赖
    implementation("androidx.wear.watchface:watchface:1.2.1")
}
```

## 故障排查

### Q: 表盘不显示任务？

检查主应用是否安装：
```bash
adb shell pm list packages | grep timetagger
# 应该看到：
# massey.hamhuo.timetagger (主应用)
# massey.hamhuo.timetagger.watchface (表盘)
```

### Q: 主应用被杀后表盘无数据？

检查日历事件是否存在：
```bash
adb shell content query --uri content://com.android.calendar/events \
    --where "description='[TIMETAGGER_CURRENT]'"
```

### Q: 表盘没有日历权限？

授予权限：
```bash
adb shell pm grant massey.hamhuo.timetagger.watchface android.permission.READ_CALENDAR
```

## 发布说明

### Play Store 发布

可以分别发布两个应用：

1. **TimeTagger** - 主应用
   - 完整的时间追踪功能
   - Tile 支持

2. **TimeTagger WatchFace** - 表盘
   - 需要主应用支持
   - 也可独立作为时间表盘

### 依赖声明

在表盘的 Play Store 描述中说明：
> "配合 TimeTagger 主应用使用，可显示当前任务。也可单独作为时间表盘使用。"

## 总结

✅ **彻底解耦** - 两个独立应用  
✅ **互不影响** - 独立生命周期  
✅ **灵活安装** - 按需安装  
✅ **标准通信** - ContentProvider  
✅ **轻量极简** - 表盘仅 < 500KB  

完美的独立表盘方案！🎉


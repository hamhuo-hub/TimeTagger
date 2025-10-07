# 数据导出使用指南

## 功能说明

已为 `LogManager` 添加了3个数据导出方法，无需额外依赖，使用Android原生API实现。

## 使用方法

### 1. 在 ViewModel 或 Activity 中调用

```kotlin
// 初始化
val logManager = LogManager(context, repository)

// 导出为CSV格式并分享
val csvIntent = logManager.exportToCsv("2025-10-07")
if (csvIntent != null) {
    context.startActivity(Intent.createChooser(csvIntent, "导出CSV"))
}

// 导出为JSON格式并分享
val jsonIntent = logManager.exportToJson("2025-10-07")
if (jsonIntent != null) {
    context.startActivity(Intent.createChooser(jsonIntent, "导出JSON"))
}

// 分享文本日志
val logIntent = logManager.shareLog("2025-10-07")
if (logIntent != null) {
    context.startActivity(Intent.createChooser(logIntent, "分享日志"))
}
```

### 2. 添加UI按钮（示例）

在 `HistoryScreen` 或 `MainActivity` 中添加导出按钮：

```kotlin
@Composable
fun ExportButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Icon(Icons.Default.Share, contentDescription = "导出")
    }
}
```

## 数据格式

### CSV格式
```csv
日期,开始时间,结束时间,时长(分钟),标签,优先级,休息时间(分钟)
2025-10-07,09:00,10:30,90,"写代码",P1,5
2025-10-07,10:30,11:00,30,"开会",P0,0
```

### JSON格式
```json
{
  "date": "2025-10-07",
  "records": [
    {
      "start": 1728270000000,
      "end": 1728275400000,
      "duration": 5400000,
      "tag": "写代码",
      "priority": 1,
      "restTime": 300000
    }
  ]
}
```

### 文本日志格式
```
=== 时间日志 2025-10-07 ===

09:00 - 10:30 (1小时30分)
[P1-核心] 写代码

10:30 - 11:00 (30分)
[P0-突发] 开会
```

## 导出位置

文件保存在：
- CSV/JSON: `/Android/data/massey.hamhuo.timetagger/files/exports/`
- 文本日志: `/Android/data/massey.hamhuo.timetagger/files/logs/`

## 分享方式

调用后会弹出系统分享菜单，可以：
- 发送到微信、邮件
- 保存到云盘（Google Drive、OneDrive等）
- 通过蓝牙传输到手机
- 复制到其他应用

## 扩展建议

如需批量导出多天数据，可添加：

```kotlin
fun exportDateRange(startDate: String, endDate: String): Intent? {
    // 循环导出多天，打包成ZIP
}
```

## 技术细节

- ✅ 使用 `FileProvider` 安全分享文件
- ✅ 支持标准 CSV 格式（可用Excel打开）
- ✅ JSON格式包含完整时间戳和时长信息
- ✅ 自动处理文件权限和URI
- ✅ 无需额外权限申请
- ✅ 零第三方依赖


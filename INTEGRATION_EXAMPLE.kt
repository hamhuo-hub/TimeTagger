// ==================== 快速集成示例 ====================
// 复制以下代码到你的 MainActivity 或 ViewModel 中

// 1. 在 TimeTrackerViewModel 中添加导出方法
class TimeTrackerViewModel(...) : ViewModel() {
    
    private val logManager = LogManager(context, repository)
    
    /**
     * 导出今天的数据为CSV
     */
    fun exportTodayCsv(): Intent? {
        val today = repository.getTodayDate()
        return logManager.exportToCsv(today)
    }
    
    /**
     * 导出今天的数据为JSON
     */
    fun exportTodayJson(): Intent? {
        val today = repository.getTodayDate()
        return logManager.exportToJson(today)
    }
    
    /**
     * 分享今天的日志
     */
    fun shareTodayLog(): Intent? {
        val today = repository.getTodayDate()
        return logManager.shareLog(today)
    }
}

// 2. 在 MainActivity 中添加导出按钮和逻辑
@Composable
fun MainScreen(viewModel: TimeTrackerViewModel) {
    val context = LocalContext.current
    
    // ... 你的现有UI代码 ...
    
    // 添加一个导出菜单按钮
    Button(
        onClick = {
            // 方式1：导出CSV
            val intent = viewModel.exportTodayCsv()
            if (intent != null) {
                context.startActivity(Intent.createChooser(intent, "导出数据"))
            }
            
            // 方式2：导出JSON
            // val intent = viewModel.exportTodayJson()
            
            // 方式3：分享文本日志
            // val intent = viewModel.shareTodayLog()
        },
        modifier = Modifier.padding(8.dp)
    ) {
        Icon(Icons.Default.Share, contentDescription = "导出数据")
        Spacer(Modifier.width(4.dp))
        Text("导出")
    }
}

// 3. 在 HistoryScreen 中添加导出按钮（可选）
@Composable
fun HistoryScreen(
    records: List<TimeRecord>,
    onBack: () -> Unit,
    onExport: () -> Unit  // 新增参数
) {
    BackHandler { onBack() }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // 在列表顶部添加导出按钮
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onExport) {
                    Text("导出CSV")
                }
                Button(onClick = { /* 导出JSON */ }) {
                    Text("导出JSON")
                }
            }
        }
        
        // 原有的记录列表
        items(records) { record ->
            // ... 你的记录展示代码 ...
        }
    }
}

// ==================== 使用场景示例 ====================

// 场景1：每日自动导出（可选）
fun autoExportDaily() {
    val yesterday = getYesterdayDate()
    logManager.saveDailyLog(yesterday)
    logManager.exportToCsv(yesterday)
}

// 场景2：手动选择日期导出
fun exportSpecificDate(date: String) {
    // 显示一个日期选择器
    // 然后导出选中的日期
    val intent = logManager.exportToCsv(date)
    context.startActivity(Intent.createChooser(intent, "导出 $date 的数据"))
}

// 场景3：批量导出（扩展功能）
fun exportLastWeek() {
    val dates = getLast7Days()
    dates.forEach { date ->
        logManager.saveDailyLog(date)
        // 可以将多个文件打包成ZIP
    }
}


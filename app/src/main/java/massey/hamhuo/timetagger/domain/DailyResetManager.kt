package massey.hamhuo.timetagger.domain

import massey.hamhuo.timetagger.data.repository.TimeTrackerRepository
import org.json.JSONObject
import java.util.*

/**
 * 每日重置管理器
 * 处理跨日任务切割和日志保存
 */
class DailyResetManager(
    private val repository: TimeTrackerRepository,
    private val logManager: LogManager
) {
    
    /**
     * 检查并执行日期重置
     */
    fun checkAndResetDaily() {
        val today = repository.getTodayDate()
        val lastDate = repository.getLastDate()
        
        if (lastDate != today) {
            handleDayChange(lastDate, today)
        }
    }
    
    /**
     * 处理日期变更
     */
    private fun handleDayChange(lastDate: String, today: String) {
        val currentTask = repository.getCurrentTask()
        val hasOngoingTask = !currentTask.isEmpty()
        
        // 如果有正在进行的任务，需要跨日处理
        if (hasOngoingTask && lastDate.isNotEmpty()) {
            splitTaskAcrossDays(lastDate, today, currentTask)
        }
        
        // 保存昨天的日志
        if (lastDate.isNotEmpty()) {
            logManager.saveDailyLog(lastDate)
        }
        
        // 更新日期
        repository.updateLastDate(today)
        
        // 如果没有正在进行的任务，清除状态
        if (!hasOngoingTask) {
            repository.clearCurrentTask()
        }
    }
    
    /**
     * 将跨日任务分割到两天
     */
    private fun splitTaskAcrossDays(
        lastDate: String,
        today: String,
        currentTask: massey.hamhuo.timetagger.data.model.CurrentTask
    ) {
        val calendar = Calendar.getInstance()
        
        // 昨天23:59:59.999结束
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfYesterday = calendar.timeInMillis
        
        // 在昨天的记录中添加结束事件
        val yesterdayEvents = repository.getEventsForDate(lastDate)
        yesterdayEvents.put(JSONObject()
            .put("ts", endOfYesterday)
            .put("tag", "")
            .put("priority", -1))
        repository.saveEventsForDate(lastDate, yesterdayEvents)
        
        // 今天00:00:00.000开始
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis
        
        // 在今天的记录中添加开始事件
        val todayEvents = repository.getEventsForDate(today)
        todayEvents.put(JSONObject()
            .put("ts", startOfToday)
            .put("tag", currentTask.tag)
            .put("priority", currentTask.priority))
        repository.saveEventsForDate(today, todayEvents)
    }
}


package massey.hamhuo.timetagger.data.repository

import android.content.Context
import android.content.SharedPreferences
import massey.hamhuo.timetagger.data.model.CurrentTask
import massey.hamhuo.timetagger.data.model.PendingTask
import massey.hamhuo.timetagger.data.model.Task
import massey.hamhuo.timetagger.data.model.TimeRecord
import massey.hamhuo.timetagger.data.storage.CrossProcessDataReader
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * 时间追踪数据仓库
 * 
 * 存储架构：
 * 1. 主存储：SharedPreferences + JSON（轻量、快速）
 * 2. 跨进程：文件共享（Tile）+ ContentProvider（独立表盘）
 * 3. 内存缓存：减少 JSON 解析，降低 GC 压力
 */
class TimeTrackerRepository(context: Context) {
    
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = 
        appContext.getSharedPreferences("time_tracker", Context.MODE_PRIVATE)
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    // 缓存：减少 JSON 解析和对象创建
    private var cachedPendingTasks: List<PendingTask>? = null
    private var cachedPendingTasksKey: String? = null
    
    private var cachedTodayRecords: List<TimeRecord>? = null
    private var cachedTodayRecordsKey: String? = null
    private var cachedTodayRecordsTime: Long = 0
    
    // ==================== 当前任务 ====================
    
    /**
     * 获取当前正在进行的任务
     */
    fun getCurrentTask(): CurrentTask {
        val tag = prefs.getString("last_tag", "") ?: ""
        val priority = prefs.getInt("last_priority", -1)
        val restTime = prefs.getLong("current_rest_time", 0)
        return CurrentTask(priority, tag, restTime)
    }
    
    /**
     * 同步当前任务到文件（供跨进程访问）
     */
    private fun syncCurrentTaskToFile() {
        try {
            val task = getCurrentTask()
            CrossProcessDataReader.saveCurrentTask(appContext, task)
        } catch (e: Exception) {
            // 忽略错误，不影响主流程
        }
    }
    
    /**
     * 更新当前任务标签
     */
    fun updateCurrentTaskTag(tag: String) {
        prefs.edit().putString("last_tag", tag).apply()
        syncCurrentTaskToFile()
    }
    
    /**
     * 清除当前任务
     */
    fun clearCurrentTask() {
        prefs.edit()
            .putString("last_tag", "")
            .putInt("last_priority", -1)
            .putLong("current_rest_time", 0)
            .apply()
        syncCurrentTaskToFile()
    }
    
    /**
     * 添加休息时间到当前任务
     */
    fun addRestTimeToCurrentTask(restMillis: Long) {
        val currentRestTime = prefs.getLong("current_rest_time", 0)
        prefs.edit().putLong("current_rest_time", currentRestTime + restMillis).apply()
        syncCurrentTaskToFile()
    }
    
    /**
     * 获取当前任务的休息时间
     */
    fun getCurrentRestTime(): Long {
        return prefs.getLong("current_rest_time", 0)
    }
    
    /**
     * 保存休息开始时间
     */
    fun setRestStartTime(timestamp: Long) {
        prefs.edit().putLong("rest_start_time", timestamp).apply()
    }
    
    /**
     * 获取休息开始时间（0表示没有在休息）
     */
    fun getRestStartTime(): Long {
        return prefs.getLong("rest_start_time", 0)
    }
    
    /**
     * 清除休息开始时间
     */
    fun clearRestStartTime() {
        prefs.edit().putLong("rest_start_time", 0).apply()
    }
    
    // ==================== 任务事件 ====================
    
    /**
     * 添加任务事件
     */
    fun addTaskEvent(task: Task) {
        val now = task.timestamp
        val day = dateFormat.format(Date(now))
        val key = "events_$day"
        val arr = JSONArray(prefs.getString(key, "[]"))
        
        arr.put(JSONObject()
            .put("ts", now)
            .put("tag", task.tag)
            .put("priority", task.priority))
        
        prefs.edit()
            .putString(key, arr.toString())
            .putString("last_tag", task.tag)
            .putInt("last_priority", task.priority)
            .putString("last_date", day)
            .putLong("current_rest_time", 0)
            .apply()
        
        syncCurrentTaskToFile()
        invalidateTodayRecordsCache()  // 清除缓存
    }
    
    /**
     * 添加任务结束事件
     */
    fun addTaskEndEvent(timestamp: Long = System.currentTimeMillis()) {
        val day = dateFormat.format(Date(timestamp))
        val key = "events_$day"
        val arr = JSONArray(prefs.getString(key, "[]"))
        
        // 保存当前任务的休息时间到最后一个任务事件
        val currentRestTime = prefs.getLong("current_rest_time", 0)
        if (arr.length() > 0) {
            val lastIndex = arr.length() - 1
            val lastEvent = arr.getJSONObject(lastIndex)
            if (lastEvent.optString("tag").isNotEmpty()) {
                lastEvent.put("restTime", currentRestTime)
                arr.put(lastIndex, lastEvent)
            }
        }
        
        arr.put(JSONObject()
            .put("ts", timestamp)
            .put("tag", "")
            .put("priority", -1))
        
        prefs.edit()
            .putString(key, arr.toString())
            .putLong("current_rest_time", 0)
            .apply()
        
        syncCurrentTaskToFile()
        invalidateTodayRecordsCache()  // 清除缓存
    }
    
    /**
     * 获取今天的时间记录
     * 优化：使用缓存，5秒内的重复请求返回缓存数据
     */
    fun getTodayRecords(): List<TimeRecord> {
        val key = "events_${dateFormat.format(Date())}"
        val now = System.currentTimeMillis()
        
        // 检查缓存（5秒内有效）
        if (cachedTodayRecords != null && 
            cachedTodayRecordsKey == key && 
            now - cachedTodayRecordsTime < 5000) {
            return cachedTodayRecords!!
        }
        
        val arr = try { 
            JSONArray(prefs.getString(key, "[]")) 
        } catch (_: Exception) { 
            JSONArray() 
        }
        
        if (arr.length() == 0) {
            val emptyList = emptyList<TimeRecord>()
            cachedTodayRecords = emptyList
            cachedTodayRecordsKey = key
            cachedTodayRecordsTime = now
            return emptyList
        }
        
        val records = mutableListOf<TimeRecord>()
        val currentTask = getCurrentTask()
        val hasCurrentTask = currentTask.tag.isNotEmpty()
        
        for (i in 0 until arr.length()) {
            val cur = arr.getJSONObject(i)
            val start = cur.optLong("ts")
            val tag = cur.optString("tag")
            val priority = cur.optInt("priority", -1)
            val restTime = cur.optLong("restTime", 0)
            
            if (tag.isEmpty()) continue
            
            val end = if (i + 1 < arr.length()) {
                arr.getJSONObject(i + 1).optLong("ts")
            } else {
                if (hasCurrentTask) System.currentTimeMillis() else start + 60000
            }
            
            // 如果是当前任务，使用实时休息时间
            val finalRestTime = if (hasCurrentTask && i == arr.length() - 1) {
                currentTask.restTime
            } else {
                restTime
            }
            
            records += TimeRecord(start, end, tag, priority, finalRestTime)
        }
        
        // 缓存结果
        cachedTodayRecords = records
        cachedTodayRecordsKey = key
        cachedTodayRecordsTime = now
        
        return records
    }
    
    /**
     * 清除今日记录缓存（添加/修改任务时调用）
     */
    private fun invalidateTodayRecordsCache() {
        cachedTodayRecords = null
        cachedTodayRecordsKey = null
        cachedTodayRecordsTime = 0
    }
    
    /**
     * 获取指定日期的事件数据（用于跨日处理）
     */
    fun getEventsForDate(date: String): JSONArray {
        return JSONArray(prefs.getString("events_$date", "[]"))
    }
    
    /**
     * 保存指定日期的事件数据
     */
    fun saveEventsForDate(date: String, events: JSONArray) {
        prefs.edit().putString("events_$date", events.toString()).apply()
    }
    
    // ==================== 待办队列 ====================
    
    /**
     * 添加到待办队列
     */
    fun addToPendingQueue(task: Task) {
        val queue = JSONArray(prefs.getString("pending_queue", "[]"))
        queue.put(JSONObject()
            .put("priority", task.priority)
            .put("tag", task.tag)
            .put("addTime", task.timestamp))
        prefs.edit().putString("pending_queue", queue.toString()).apply()
        invalidatePendingTasksCache()  // 清除缓存
    }
    
    /**
     * 获取待办任务列表
     * 优化：使用缓存，避免频繁 JSON 解析
     */
    fun getPendingTasks(): List<PendingTask> {
        val key = prefs.getString("pending_queue", "[]") ?: "[]"
        
        // 检查缓存
        if (cachedPendingTasks != null && cachedPendingTasksKey == key) {
            return cachedPendingTasks!!
        }
        
        val queue = JSONArray(key)
        val tasks = mutableListOf<PendingTask>()
        for (i in 0 until queue.length()) {
            val obj = queue.getJSONObject(i)
            tasks.add(PendingTask(
                priority = obj.getInt("priority"),
                tag = obj.getString("tag"),
                addTime = obj.getLong("addTime")
            ))
        }
        
        // 缓存结果
        cachedPendingTasks = tasks
        cachedPendingTasksKey = key
        
        return tasks
    }
    
    /**
     * 清除待办任务缓存（修改队列时调用）
     */
    private fun invalidatePendingTasksCache() {
        cachedPendingTasks = null
        cachedPendingTasksKey = null
    }
    
    /**
     * 更新待办队列
     */
    fun updatePendingQueue(tasks: List<PendingTask>) {
        val queue = JSONArray()
        tasks.forEach {
            queue.put(JSONObject()
                .put("priority", it.priority)
                .put("tag", it.tag)
                .put("addTime", it.addTime))
        }
        prefs.edit().putString("pending_queue", queue.toString()).apply()
        invalidatePendingTasksCache()  // 清除缓存
    }
    
    /**
     * 清空待办队列
     */
    fun clearPendingQueue() {
        prefs.edit().putString("pending_queue", "[]").apply()
        invalidatePendingTasksCache()  // 清除缓存
    }
    
    // ==================== 日期管理 ====================
    
    /**
     * 获取上次记录的日期
     */
    fun getLastDate(): String {
        return prefs.getString("last_date", "") ?: ""
    }
    
    /**
     * 更新最后日期
     */
    fun updateLastDate(date: String) {
        prefs.edit().putString("last_date", date).apply()
    }
    
    /**
     * 获取今天的日期字符串
     */
    fun getTodayDate(): String {
        return dateFormat.format(Date())
    }
}


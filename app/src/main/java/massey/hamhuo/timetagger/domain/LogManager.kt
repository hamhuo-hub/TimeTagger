package massey.hamhuo.timetagger.domain

import android.content.Context
import massey.hamhuo.timetagger.data.repository.TimeTrackerRepository
import massey.hamhuo.timetagger.util.DateFormatter
import org.json.JSONArray
import java.io.File

/**
 * 日志管理器
 * 负责生成和保存每日时间日志
 */
class LogManager(
    private val context: Context,
    private val repository: TimeTrackerRepository
) {
    
    /**
     * 保存指定日期的日志到文件
     */
    fun saveDailyLog(date: String) {
        try {
            val events = repository.getEventsForDate(date)
            if (events.length() == 0) return
            
            val logContent = generateLogContent(date, events)
            writeLogToFile(date, logContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 生成日志内容
     */
    private fun generateLogContent(date: String, events: JSONArray): String {
        val sb = StringBuilder()
        sb.appendLine("=== 时间日志 $date ===\n")
        
        for (i in 0 until events.length()) {
            val cur = events.getJSONObject(i)
            val start = cur.optLong("ts")
            val tag = cur.optString("tag")
            val priority = cur.optInt("priority", -1)
            
            if (tag.isEmpty()) continue
            
            val end = if (i + 1 < events.length()) {
                events.getJSONObject(i + 1).optLong("ts")
            } else {
                start + 60000
            }
            
            val startStr = DateFormatter.formatTime(start)
            val endStr = DateFormatter.formatTime(end)
            val duration = DateFormatter.formatDuration(end - start)
            val priorityName = getPriorityName(priority)
            
            sb.appendLine("$startStr - $endStr ($duration)")
            sb.appendLine("[$priorityName] $tag")
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    /**
     * 将日志写入文件
     */
    private fun writeLogToFile(date: String, content: String) {
        val externalDir = context.getExternalFilesDir(null) ?: return
        val logDir = File(externalDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()
        
        val logFile = File(logDir, "$date.txt")
        logFile.writeText(content)
    }
    
    /**
     * 获取优先级名称
     */
    private fun getPriorityName(priority: Int): String {
        return when (priority) {
            0 -> "P0-突发"
            1 -> "P1-核心"
            2 -> "P2-短期"
            else -> "未分类"
        }
    }
}


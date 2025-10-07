package massey.hamhuo.timetagger.domain

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import massey.hamhuo.timetagger.data.repository.TimeTrackerRepository
import massey.hamhuo.timetagger.util.DateFormatter
import org.json.JSONArray
import org.json.JSONObject
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
    
    // ==================== 数据导出功能 ====================
    
    /**
     * 导出为CSV格式并分享
     */
    fun exportToCsv(date: String): Intent? {
        try {
            val events = repository.getEventsForDate(date)
            if (events.length() == 0) return null
            
            val csvContent = generateCsvContent(date, events)
            val file = writeExportFile(date, csvContent, "csv")
            return createShareIntent(file, "text/csv")
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * 导出为JSON格式并分享
     */
    fun exportToJson(date: String): Intent? {
        try {
            val events = repository.getEventsForDate(date)
            if (events.length() == 0) return null
            
            val jsonContent = generateJsonContent(date, events)
            val file = writeExportFile(date, jsonContent, "json")
            return createShareIntent(file, "application/json")
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * 分享现有日志文件
     */
    fun shareLog(date: String): Intent? {
        try {
            // 确保日志文件存在
            saveDailyLog(date)
            
            val externalDir = context.getExternalFilesDir(null) ?: return null
            val logFile = File(File(externalDir, "logs"), "$date.txt")
            
            if (!logFile.exists()) return null
            
            return createShareIntent(logFile, "text/plain")
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * 生成CSV内容
     */
    private fun generateCsvContent(date: String, events: JSONArray): String {
        val sb = StringBuilder()
        sb.appendLine("日期,开始时间,结束时间,时长(分钟),标签,优先级,休息时间(分钟)")
        
        for (i in 0 until events.length()) {
            val cur = events.getJSONObject(i)
            val start = cur.optLong("ts")
            val tag = cur.optString("tag")
            val priority = cur.optInt("priority", -1)
            val restTime = cur.optLong("restTime", 0)
            
            if (tag.isEmpty()) continue
            
            val end = if (i + 1 < events.length()) {
                events.getJSONObject(i + 1).optLong("ts")
            } else {
                start + 60000
            }
            
            val durationMinutes = (end - start) / 60000
            val restMinutes = restTime / 60000
            
            sb.appendLine("$date,${DateFormatter.formatTime(start)},${DateFormatter.formatTime(end)},$durationMinutes,\"$tag\",P$priority,$restMinutes")
        }
        
        return sb.toString()
    }
    
    /**
     * 生成JSON内容
     */
    private fun generateJsonContent(date: String, events: JSONArray): String {
        val root = JSONObject()
        root.put("date", date)
        
        val records = JSONArray()
        for (i in 0 until events.length()) {
            val cur = events.getJSONObject(i)
            val start = cur.optLong("ts")
            val tag = cur.optString("tag")
            val priority = cur.optInt("priority", -1)
            val restTime = cur.optLong("restTime", 0)
            
            if (tag.isEmpty()) continue
            
            val end = if (i + 1 < events.length()) {
                events.getJSONObject(i + 1).optLong("ts")
            } else {
                start + 60000
            }
            
            val record = JSONObject()
            record.put("start", start)
            record.put("end", end)
            record.put("duration", end - start)
            record.put("tag", tag)
            record.put("priority", priority)
            record.put("restTime", restTime)
            
            records.put(record)
        }
        
        root.put("records", records)
        return root.toString(2)
    }
    
    /**
     * 写入导出文件
     */
    private fun writeExportFile(date: String, content: String, extension: String): File {
        val externalDir = context.getExternalFilesDir(null)!!
        val exportDir = File(externalDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        
        val file = File(exportDir, "timetagger_$date.$extension")
        file.writeText(content)
        return file
    }
    
    /**
     * 创建分享Intent
     */
    private fun createShareIntent(file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}


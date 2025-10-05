package massey.hamhuo.timetagger.util

import java.text.SimpleDateFormat
import java.util.*

/**
 * 日期格式化工具
 */
object DateFormatter {
    
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    /**
     * 格式化时间为 HH:mm
     */
    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }
    
    /**
     * 格式化日期为 yyyy-MM-dd
     */
    fun formatDate(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }
    
    /**
     * 格式化时长
     */
    fun formatDuration(ms: Long): String {
        val totalMinutes = ms / 60000
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }
    
    /**
     * 获取当前时间字符串 HH:mm
     */
    fun getCurrentTime(): String {
        return timeFormat.format(Date())
    }
}


package massey.hamhuo.timetagger.util

import java.text.SimpleDateFormat
import java.util.*

/**
 * Date Formatter
 */
object DateFormatter {
    
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    // Format time
    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }

    // Format duration
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
    
    // Get current time
    fun getCurrentTime(): String {
        return timeFormat.format(Date())
    }
}


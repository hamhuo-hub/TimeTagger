package massey.hamhuo.timetagger.watchface

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri

/**
 * 当前任务数据读取器
 * 通过 ContentProvider 从主应用读取数据
 * 
 * 注意：主应用必须正在运行，ContentProvider 才可访问
 */
class CurrentTaskReader(private val context: Context) {

    companion object {
        private const val AUTHORITY = "massey.hamhuo.timetagger.provider"
        private val CONTENT_URI = "content://$AUTHORITY/current_task".toUri()
        
        private const val COLUMN_PRIORITY = "priority"
        private const val COLUMN_TAG = "tag"
        private const val COLUMN_REST_TIME = "rest_time"
    }

    data class CurrentTask(
        val priority: Int,
        val tag: String,
        val restTime: Long
    ) {
        companion object {
            fun empty() = CurrentTask(-1, "", 0)
        }
    }

    /**
     * 从主应用 ContentProvider 读取当前任务
     */
    fun getCurrentTask(): CurrentTask {
        return try {
            context.contentResolver.query(
                CONTENT_URI,
                arrayOf(COLUMN_PRIORITY, COLUMN_TAG, COLUMN_REST_TIME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val priority = cursor.getInt(0)
                    val tag = cursor.getString(1) ?: ""
                    val restTime = cursor.getLong(2)
                    CurrentTask(priority, tag, restTime)
                } else {
                    CurrentTask.empty()
                }
            } ?: CurrentTask.empty()
        } catch (e: Exception) {
            // 主应用未运行或无数据
            CurrentTask.empty()
        }
    }
}



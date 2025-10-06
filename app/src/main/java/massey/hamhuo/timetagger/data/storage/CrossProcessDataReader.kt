package massey.hamhuo.timetagger.data.storage

import android.content.Context
import massey.hamhuo.timetagger.data.model.CurrentTask
import java.io.File

/**
 * 跨进程数据读取器
 * 专门用于表盘和Tile服务读取当前任务状态
 * 使用文件存储，支持跨进程访问
 */
class CrossProcessDataReader(context: Context) {
    
    private val currentTaskFile = File(context.filesDir, "current_task.txt")
    
    /**
     * 读取当前任务
     * 格式: priority|tag|restTime
     */
    fun getCurrentTask(): CurrentTask {
        return try {
            if (!currentTaskFile.exists()) {
                return CurrentTask.empty()
            }
            
            val data = currentTaskFile.readText()
            if (data.isEmpty()) {
                return CurrentTask.empty()
            }
            
            val parts = data.split("|")
            if (parts.size < 2) {
                return CurrentTask.empty()
            }
            
            val priority = parts[0].toIntOrNull() ?: -1
            val tag = parts.getOrNull(1) ?: ""
            val restTime = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            
            CurrentTask(priority, tag, restTime)
        } catch (e: Exception) {
            CurrentTask.empty()
        }
    }
    
    companion object {
        /**
         * 保存当前任务（由主应用调用）
         */
        fun saveCurrentTask(context: Context, task: CurrentTask) {
            try {
                val file = File(context.filesDir, "current_task.txt")
                val data = "${task.priority}|${task.tag}|${task.restTime}"
                file.writeText(data)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}


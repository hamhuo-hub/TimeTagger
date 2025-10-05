package massey.hamhuo.timetagger.data.model

/**
 * 任务数据模型
 */
data class Task(
    val priority: Int,  // 0=P0重要紧急, 1=P1重要不紧急, 2=P2紧急不重要, 3=P3不重要不紧急
    val tag: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 当前任务状态
 */
data class CurrentTask(
    val priority: Int,
    val tag: String
) {
    fun isEmpty(): Boolean = tag.isEmpty() || priority < 0
    
    companion object {
        fun empty() = CurrentTask(-1, "")
    }
}

/**
 * 待办任务
 */
data class PendingTask(
    val priority: Int,
    val tag: String,
    val addTime: Long
)

/**
 * 时间记录行
 */
data class TimeRecord(
    val start: Long,
    val end: Long,
    val tag: String,
    val priority: Int
)


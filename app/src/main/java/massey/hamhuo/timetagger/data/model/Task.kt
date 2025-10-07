package massey.hamhuo.timetagger.data.model

/**
 * 任务数据模型
 */
data class Task(
    val priority: Int,  // 0=P0突发, 1=P1核心, 2=P2短期
    val tag: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 当前任务状态
 */
data class CurrentTask(
    val priority: Int,
    val tag: String,
    val restTime: Long = 0  // 已休息时间（毫秒）
) {
    fun isEmpty(): Boolean = tag.isEmpty() || priority < 0
    
    companion object {
        fun empty() = CurrentTask(-1, "", 0)
    }
}

/**
 * 建议任务（完成任务后的待办建议）
 */
data class SuggestedTask(
    val priority: Int,
    val tag: String
) {
    fun isEmpty(): Boolean = tag.isEmpty() || priority < 0
    
    companion object {
        fun empty() = SuggestedTask(-1, "")
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
    val priority: Int,
    val restTime: Long = 0  // 休息时间（毫秒）
)


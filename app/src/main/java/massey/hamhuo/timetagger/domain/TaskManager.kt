package massey.hamhuo.timetagger.domain

import massey.hamhuo.timetagger.data.model.PendingTask
import massey.hamhuo.timetagger.data.model.Task
import massey.hamhuo.timetagger.data.repository.TimeTrackerRepository

/**
 * 任务管理器 - 业务逻辑层
 * 负责任务队列管理和优先级处理
 */
class TaskManager(private val repository: TimeTrackerRepository) {
    
    /**
     * 添加任务
     * 根据优先级决定立即开始还是加入队列
     */
    fun addTask(priority: Int, label: String) {
        val currentTask = repository.getCurrentTask()
        val currentPriority = currentTask.priority
        
        // 如果当前有任务且新任务优先级更低，加入待办队列
        if (currentPriority >= 0 && priority > currentPriority) {
            repository.addToPendingQueue(Task(priority, label))
        } else {
            // 直接开始任务
            startTask(priority, label)
        }
    }
    
    /**
     * 开始任务
     */
    fun startTask(priority: Int, label: String) {
        val task = Task(priority, label)
        repository.addTaskEvent(task)
    }
    
    /**
     * 完成当前任务
     * 自动从队列中取下一个任务
     */
    fun completeTask() {
        // 结束当前任务
        repository.addTaskEndEvent()
        repository.clearCurrentTask()
        
        // 从队列中取下一个任务
        val pendingTasks = repository.getPendingTasks()
        if (pendingTasks.isNotEmpty()) {
            // 按优先级排序（P0 > P1 > P2 > P3），同优先级按时间排序
            val sortedTasks = pendingTasks.sortedWith(
                compareBy({ it.priority }, { it.addTime })
            )
            
            // 取出第一个任务
            val nextTask = sortedTasks.first()
            startTask(nextTask.priority, nextTask.tag)
            
            // 从队列中移除
            repository.updatePendingQueue(sortedTasks.drop(1))
        }
    }
    
    /**
     * 更新当前任务标签
     */
    fun updateCurrentTaskTag(label: String) {
        repository.updateCurrentTaskTag(label)
    }
    
    /**
     * 获取待办任务数量
     */
    fun getPendingTaskCount(): Int {
        return repository.getPendingTasks().size
    }
}


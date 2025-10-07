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
        
        // 如果当前有任务且新任务优先级相同或更低，加入待办队列
        if (currentPriority >= 0 && priority >= currentPriority) {
            repository.addToPendingQueue(Task(priority, label))
        } else {
            // 只有新任务优先级更高时，才直接开始（打断当前任务）
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
     * 不自动开始下一个任务，待办队列保持不变
     */
    fun completeTask() {
        // 结束当前任务
        repository.addTaskEndEvent()
        repository.clearCurrentTask()
        // 待办队列保持不变，由用户决定是否执行
    }
    
    /**
     * 开始待办队列中的第一个任务（用户确认建议后）
     */
    fun startFirstPendingTask() {
        val pendingTasks = repository.getPendingTasks()
        if (pendingTasks.isNotEmpty()) {
            // 按优先级排序（P0 > P1 > P2），同优先级按时间排序
            val sortedTasks = pendingTasks.sortedWith(
                compareBy({ it.priority }, { it.addTime })
            )
            
            // 取出第一个任务并开始
            val firstTask = sortedTasks.first()
            startTask(firstTask.priority, firstTask.tag)
            
            // 从待办队列中移除
            repository.updatePendingQueue(sortedTasks.drop(1))
        }
    }
    
    /**
     * 从待办队列中选择并开始指定的任务
     */
    fun startPendingTask(pendingTask: PendingTask) {
        // 开始选中的任务
        startTask(pendingTask.priority, pendingTask.tag)
        
        // 从待办队列中移除这个任务
        val pendingTasks = repository.getPendingTasks()
        val updatedTasks = pendingTasks.filter { 
            !(it.priority == pendingTask.priority && it.tag == pendingTask.tag && it.addTime == pendingTask.addTime)
        }
        repository.updatePendingQueue(updatedTasks)
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


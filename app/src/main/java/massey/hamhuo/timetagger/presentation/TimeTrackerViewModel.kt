package massey.hamhuo.timetagger.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.StateFlow
import massey.hamhuo.timetagger.data.model.CurrentTask
import massey.hamhuo.timetagger.data.model.PendingTask
import massey.hamhuo.timetagger.data.model.SuggestedTask
import massey.hamhuo.timetagger.data.model.TimeRecord
import massey.hamhuo.timetagger.service.TimeTrackerService

/**
 * 时间追踪ViewModel
 * 作为Service和UI之间的桥梁
 */
class TimeTrackerViewModel(
    private val service: TimeTrackerService
) : ViewModel() {
    
    // 休息状态（从Service获取）
    val isResting: StateFlow<Boolean> = service.isResting
    val restTimeLeft: StateFlow<Long> = service.restTimeLeft
    
    /**
     * 添加任务（委托给Service）
     */
    fun addTask(priority: Int, label: String) {
        service.addTask(priority, label)
    }
    
    /**
     * 直接添加任务到待办队列
     */
    fun addPendingTask(priority: Int, label: String) {
        service.addPendingTask(priority, label)
    }
    
    /**
     * 完成当前任务
     */
    fun completeTask() {
        service.completeTask()
    }
    
    /**
     * 更新当前任务标签
     */
    fun updateCurrentTaskTag(label: String) {
        service.updateCurrentTaskTag(label)
    }
    
    /**
     * 获取当前任务
     */
    fun getCurrentTask(): CurrentTask {
        return service.getCurrentTask()
    }
    
    /**
     * 获取待办任务列表
     */
    fun getPendingTasks(): List<PendingTask> {
        return service.getPendingTasks()
    }
    
    /**
     * 获取今天的记录
     */
    fun getTodayRecords(): List<TimeRecord> {
        return service.getTodayRecords()
    }
    
    /**
     * 获取待办任务数量
     */
    fun getPendingTaskCount(): Int {
        return service.getPendingTaskCount()
    }
    
    /**
     * 获取建议任务（待办队列的第一个）
     */
    fun getSuggestedTask(): SuggestedTask {
        return service.getSuggestedTask()
    }
    
    /**
     * 接受建议的任务（开始待办队列第一个任务）
     */
    fun acceptSuggestedTask() {
        service.acceptSuggestedTask()
    }
    
    /**
     * 从待办列表中选择并开始任务
     */
    fun startPendingTask(task: PendingTask) {
        service.startPendingTask(task)
    }
    
    /**
     * 开始任务内休息（5分钟）
     */
    fun startTaskRest() {
        service.startTaskRest()
    }
    
    /**
     * 停止休息，继续工作
     */
    fun stopTaskRest() {
        service.stopTaskRest()
    }
}

/**
 * ViewModel工厂
 */
class TimeTrackerViewModelFactory(
    private val service: TimeTrackerService
) : ViewModelProvider.Factory {
    
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimeTrackerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TimeTrackerViewModel(service) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


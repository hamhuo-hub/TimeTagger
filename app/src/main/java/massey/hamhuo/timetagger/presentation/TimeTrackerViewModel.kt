package massey.hamhuo.timetagger.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import massey.hamhuo.timetagger.data.model.CurrentTask
import massey.hamhuo.timetagger.data.model.PendingTask
import massey.hamhuo.timetagger.data.model.TimeRecord
import massey.hamhuo.timetagger.data.repository.TimeTrackerRepository
import massey.hamhuo.timetagger.domain.DailyResetManager
import massey.hamhuo.timetagger.domain.LogManager
import massey.hamhuo.timetagger.domain.TaskManager

/**
 * 时间追踪ViewModel
 * 管理UI状态和业务逻辑调用
 */
class TimeTrackerViewModel(
    private val repository: TimeTrackerRepository,
    private val taskManager: TaskManager,
    private val dailyResetManager: DailyResetManager
) : ViewModel() {
    
    // UI状态
    private val _currentTask = MutableStateFlow(CurrentTask.empty())
    val currentTask: StateFlow<CurrentTask> = _currentTask.asStateFlow()
    
    private val _pendingTasks = MutableStateFlow<List<PendingTask>>(emptyList())
    val pendingTasks: StateFlow<List<PendingTask>> = _pendingTasks.asStateFlow()
    
    private val _todayRecords = MutableStateFlow<List<TimeRecord>>(emptyList())
    val todayRecords: StateFlow<List<TimeRecord>> = _todayRecords.asStateFlow()
    
    init {
        // 初始化时检查日期
        dailyResetManager.checkAndResetDaily()
        refreshState()
    }
    
    /**
     * 添加任务
     */
    fun addTask(priority: Int, label: String) {
        taskManager.addTask(priority, label)
        refreshState()
    }
    
    /**
     * 完成当前任务
     */
    fun completeTask() {
        taskManager.completeTask()
        refreshState()
    }
    
    /**
     * 更新当前任务标签
     */
    fun updateCurrentTaskTag(label: String) {
        taskManager.updateCurrentTaskTag(label)
        refreshState()
    }
    
    /**
     * 获取当前任务
     */
    fun getCurrentTask(): CurrentTask {
        return repository.getCurrentTask()
    }
    
    /**
     * 获取待办任务列表
     */
    fun getPendingTasks(): List<PendingTask> {
        return repository.getPendingTasks()
    }
    
    /**
     * 获取今天的记录
     */
    fun getTodayRecords(): List<TimeRecord> {
        return repository.getTodayRecords()
    }
    
    /**
     * 获取待办任务数量
     */
    fun getPendingTaskCount(): Int {
        return taskManager.getPendingTaskCount()
    }
    
    /**
     * 刷新状态
     */
    private fun refreshState() {
        _currentTask.value = repository.getCurrentTask()
        _pendingTasks.value = repository.getPendingTasks()
        _todayRecords.value = repository.getTodayRecords()
    }
}

/**
 * ViewModel工厂
 */
class TimeTrackerViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimeTrackerViewModel::class.java)) {
            val repository = TimeTrackerRepository(context)
            val taskManager = TaskManager(repository)
            val logManager = LogManager(context, repository)
            val dailyResetManager = DailyResetManager(repository, logManager)
            
            @Suppress("UNCHECKED_CAST")
            return TimeTrackerViewModel(repository, taskManager, dailyResetManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


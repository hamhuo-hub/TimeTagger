package massey.hamhuo.timetagger.presentation

import android.content.Context
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
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
    private val context: Context,
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
    
    // 休息状态
    private val _isResting = MutableStateFlow(false)
    val isResting: StateFlow<Boolean> = _isResting.asStateFlow()
    
    private val _restTimeLeft = MutableStateFlow(0L)
    val restTimeLeft: StateFlow<Long> = _restTimeLeft.asStateFlow()
    
    private var restTimer: CountDownTimer? = null
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    
    init {
        // 初始化时检查日期
        dailyResetManager.checkAndResetDaily()
        refreshState()
    }
    
    override fun onCleared() {
        super.onCleared()
        restTimer?.cancel()
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
     * 开始任务内休息（5分钟）
     * 任务继续进行，休息时间计入任务总时长
     */
    fun startTaskRest() {
        // 只有在有任务进行时才能休息
        if (_currentTask.value.priority < 0 || _isResting.value) return
        
        _isResting.value = true
        _restTimeLeft.value = 5 * 60 * 1000L // 5分钟
        
        restTimer?.cancel()
        restTimer = object : CountDownTimer(5 * 60 * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                _restTimeLeft.value = millisUntilFinished
            }
            
            override fun onFinish() {
                _isResting.value = false
                _restTimeLeft.value = 0L
                // 记录5分钟休息时间
                repository.addRestTimeToCurrentTask(5 * 60 * 1000L)
                refreshState()
                // 振动提醒继续工作
                vibrateNotification()
            }
        }.start()
    }
    
    /**
     * 停止休息，继续工作
     */
    fun stopTaskRest() {
        if (_isResting.value) {
            // 计算已休息的时间
            val restedTime = 5 * 60 * 1000L - _restTimeLeft.value
            if (restedTime > 0) {
                repository.addRestTimeToCurrentTask(restedTime)
                refreshState()
            }
        }
        restTimer?.cancel()
        _isResting.value = false
        _restTimeLeft.value = 0L
    }
    
    /**
     * 振动通知
     */
    private fun vibrateNotification() {
        if (vibrator.hasVibrator()) {
            val pattern = longArrayOf(0, 200, 100, 200, 100, 200)
            val effect = VibrationEffect.createWaveform(pattern, -1)
            vibrator.vibrate(effect)
        }
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
            return TimeTrackerViewModel(context, repository, taskManager, dailyResetManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


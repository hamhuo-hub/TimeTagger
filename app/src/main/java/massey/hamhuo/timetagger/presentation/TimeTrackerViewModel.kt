package massey.hamhuo.timetagger.presentation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import massey.hamhuo.timetagger.data.model.CurrentTask
import massey.hamhuo.timetagger.data.model.PendingTask
import massey.hamhuo.timetagger.data.model.SuggestedTask
import massey.hamhuo.timetagger.data.model.TimeRecord
import massey.hamhuo.timetagger.data.repository.TimeTrackerRepository
import massey.hamhuo.timetagger.domain.DailyResetManager
import massey.hamhuo.timetagger.domain.LogManager
import massey.hamhuo.timetagger.domain.TaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private var updateJob: Job? = null
    private val viewModelScope = CoroutineScope(Dispatchers.Main)
    
    private val REST_DURATION = 5 * 60 * 1000L // 5分钟
    
    init {
        // 初始化时检查日期
        dailyResetManager.checkAndResetDaily()
        refreshState()
        // 检查是否有未完成的休息
        checkRestoreRestState()
    }
    
    override fun onCleared() {
        super.onCleared()
        updateJob?.cancel()
    }
    
    /**
     * 检查并恢复休息状态
     */
    private fun checkRestoreRestState() {
        val restStartTime = repository.getRestStartTime()
        if (restStartTime > 0) {
            val elapsed = System.currentTimeMillis() - restStartTime
            if (elapsed < REST_DURATION) {
                // 休息还没结束，恢复倒计时显示
                _isResting.value = true
                startRestTimeUpdate()
            } else {
                // 休息已经结束，清理状态
                repository.clearRestStartTime()
                _isResting.value = false
                _restTimeLeft.value = 0L
            }
        }
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
     * 获取建议任务（待办队列的第一个）
     */
    fun getSuggestedTask(): SuggestedTask {
        val pendingTasks = repository.getPendingTasks()
        if (pendingTasks.isNotEmpty()) {
            val sortedTasks = pendingTasks.sortedWith(
                compareBy({ it.priority }, { it.addTime })
            )
            val first = sortedTasks.first()
            return SuggestedTask(first.priority, first.tag)
        }
        return SuggestedTask.empty()
    }
    
    /**
     * 接受建议的任务（开始待办队列第一个任务）
     */
    fun acceptSuggestedTask() {
        taskManager.startFirstPendingTask()
        refreshState()
    }
    
    /**
     * 从待办列表中选择并开始任务
     */
    fun startPendingTask(task: PendingTask) {
        taskManager.startPendingTask(task)
        refreshState()
    }
    
    /**
     * 开始任务内休息（5分钟）
     * 任务继续进行，休息时间计入任务总时长
     */
    fun startTaskRest() {
        // 只有在有任务进行时才能休息
        if (_currentTask.value.priority < 0 || _isResting.value) return
        
        val restStartTime = System.currentTimeMillis()
        repository.setRestStartTime(restStartTime)
        
        _isResting.value = true
        _restTimeLeft.value = REST_DURATION
        
        // 使用AlarmManager设置5分钟后的闹钟
        val intent = Intent(context, RestAlarmReceiver::class.java).apply {
            action = RestAlarmReceiver.ACTION_REST_FINISHED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            restStartTime + REST_DURATION,
            pendingIntent
        )
        
        // 启动UI更新
        startRestTimeUpdate()
    }
    
    /**
     * 停止休息，继续工作
     */
    fun stopTaskRest() {
        if (_isResting.value) {
            val restStartTime = repository.getRestStartTime()
            if (restStartTime > 0) {
                // 计算已休息的时间
                val restedTime = System.currentTimeMillis() - restStartTime
                if (restedTime > 0) {
                    repository.addRestTimeToCurrentTask(restedTime)
                    refreshState()
                }
            }
            
            // 取消AlarmManager
            cancelRestAlarm()
        }
        
        repository.clearRestStartTime()
        updateJob?.cancel()
        _isResting.value = false
        _restTimeLeft.value = 0L
    }
    
    /**
     * 开始更新休息时间显示
     */
    private fun startRestTimeUpdate() {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            while (_isResting.value) {
                val restStartTime = repository.getRestStartTime()
                if (restStartTime > 0) {
                    val elapsed = System.currentTimeMillis() - restStartTime
                    val remaining = REST_DURATION - elapsed
                    
                    if (remaining > 0) {
                        _restTimeLeft.value = remaining
                        delay(1000) // 每秒更新一次
                    } else {
                        // 时间到了
                        _isResting.value = false
                        _restTimeLeft.value = 0L
                        repository.clearRestStartTime()
                        break
                    }
                } else {
                    break
                }
            }
        }
    }
    
    /**
     * 取消休息闹钟
     */
    private fun cancelRestAlarm() {
        val intent = Intent(context, RestAlarmReceiver::class.java).apply {
            action = RestAlarmReceiver.ACTION_REST_FINISHED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
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


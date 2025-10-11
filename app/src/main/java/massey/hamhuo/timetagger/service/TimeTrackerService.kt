package massey.hamhuo.timetagger.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*
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
import massey.hamhuo.timetagger.presentation.MainActivity
import massey.hamhuo.timetagger.presentation.RestAlarmReceiver

/**
 * 时间追踪后台服务
 * 以前台服务方式运行，确保持续追踪时间
 */
class TimeTrackerService : Service() {
    
    private val binder = ServiceBinder()
    private lateinit var repository: TimeTrackerRepository
    private lateinit var taskManager: TaskManager
    private lateinit var dailyResetManager: DailyResetManager
    private lateinit var alarmManager: AlarmManager
    private lateinit var vibrator: Vibrator
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 休息状态
    private val _isResting = MutableStateFlow(false)
    val isResting: StateFlow<Boolean> = _isResting.asStateFlow()
    
    private val _restTimeLeft = MutableStateFlow(0L)
    val restTimeLeft: StateFlow<Long> = _restTimeLeft.asStateFlow()
    
    private var updateJob: Job? = null
    private val REST_DURATION = 5 * 60 * 1000L // 5分钟
    
    private val CHANNEL_ID = "time_tracker_service"
    private val NOTIFICATION_ID = 1001
    
    inner class ServiceBinder : Binder() {
        fun getService(): TimeTrackerService = this@TimeTrackerService
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化组件
        repository = TimeTrackerRepository(applicationContext)
        taskManager = TaskManager(repository)
        val logManager = LogManager(applicationContext, repository)
        dailyResetManager = DailyResetManager(repository, logManager)
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        // 创建通知渠道
        createNotificationChannel()
        
        // 启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        // 初始化时检查日期
        dailyResetManager.checkAndResetDaily()
        
        // 检查是否有未完成的休息
        checkRestoreRestState()
        
        // 不再需要定期轮询更新通知
        // 通知会在任务状态变化时自动更新（addTask, completeTask 等）
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 返回 START_STICKY 确保服务被杀死后会自动重启
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        updateJob?.cancel()
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "时间追踪服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "持续追踪时间使用情况"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val currentTask = repository.getCurrentTask()
        
        // 构建通知内容
        val contentText = if (currentTask.tag.isNotEmpty()) {
            "正在进行: ${currentTask.tag}"
        } else {
            "等待开始任务"
        }
        
        // 点击通知跳转到MainActivity
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("TimeTag 正在运行")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
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
    
    // ==================== 公共API（供Activity调用）====================
    
    /**
     * 添加任务
     */
    fun addTask(priority: Int, label: String) {
        taskManager.addTask(priority, label)
        updateNotification()
    }
    
    /**
     * 直接添加任务到待办队列
     */
    fun addPendingTask(priority: Int, label: String) {
        taskManager.addPendingTask(priority, label)
    }
    
    /**
     * 完成当前任务
     */
    fun completeTask() {
        taskManager.completeTask()
        updateNotification()
    }
    
    /**
     * 更新当前任务标签
     */
    fun updateCurrentTaskTag(label: String) {
        taskManager.updateCurrentTaskTag(label)
        updateNotification()
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
        updateNotification()
    }
    
    /**
     * 从待办列表中选择并开始任务
     */
    fun startPendingTask(task: PendingTask) {
        taskManager.startPendingTask(task)
        updateNotification()
    }
    
    /**
     * 开始任务内休息（5分钟）
     */
    fun startTaskRest() {
        if (_isResting.value) return
        
        val currentTask = getCurrentTask()
        if (currentTask.priority < 0) return
        
        val restStartTime = System.currentTimeMillis()
        repository.setRestStartTime(restStartTime)
        
        _isResting.value = true
        _restTimeLeft.value = REST_DURATION
        
        // 使用AlarmManager设置5分钟后的闹钟
        val intent = Intent(this, RestAlarmReceiver::class.java).apply {
            action = RestAlarmReceiver.ACTION_REST_FINISHED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
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
                val restedTime = System.currentTimeMillis() - restStartTime
                if (restedTime > 0) {
                    repository.addRestTimeToCurrentTask(restedTime)
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
     * 优化：只在休息时运行，使用精确延迟避免漂移
     */
    private fun startRestTimeUpdate() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            val restStartTime = repository.getRestStartTime()
            if (restStartTime <= 0) return@launch
            
            // 计算结束时间
            val endTime = restStartTime + REST_DURATION
            
            while (_isResting.value && isActive) {
                val now = System.currentTimeMillis()
                val remaining = endTime - now
                
                if (remaining > 0) {
                    _restTimeLeft.value = remaining
                    // 精确延迟到下一秒，避免累积误差
                    val delayMs = 1000 - (now % 1000)
                    delay(delayMs)
                } else {
                    // 时间到了
                    _isResting.value = false
                    _restTimeLeft.value = 0L
                    repository.clearRestStartTime()
                    vibrateNotification()
                    break
                }
            }
        }
    }
    
    /**
     * 取消休息闹钟
     */
    private fun cancelRestAlarm() {
        val intent = Intent(this, RestAlarmReceiver::class.java).apply {
            action = RestAlarmReceiver.ACTION_REST_FINISHED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
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
    
    companion object {
        /**
         * 启动服务
         */
        fun startService(context: Context) {
            val intent = Intent(context, TimeTrackerService::class.java)
            context.startForegroundService(intent)
        }
        
        /**
         * 停止服务（通常不需要调用）
         */
        fun stopService(context: Context) {
            val intent = Intent(context, TimeTrackerService::class.java)
            context.stopService(intent)
        }
    }
}


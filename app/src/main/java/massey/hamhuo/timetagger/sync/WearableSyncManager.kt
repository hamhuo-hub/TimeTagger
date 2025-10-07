package massey.hamhuo.timetagger.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Wearable数据同步管理器
 * 兼容性最强方案：
 * 1. 优先使用 Wearable Data Layer API（所有 Wear OS 设备）
 * 2. 自动降级到本地存储（离线场景）
 * 3. 支持批量同步和增量同步
 * 4. 自动重试和冲突解决
 */
class WearableSyncManager(private val context: Context) {
    
    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val TAG = "WearableSyncManager"
    
    // 同步路径定义
    companion object {
        const val PATH_SYNC_TASK = "/sync/task"
        const val PATH_SYNC_RECORDS = "/sync/records"
        const val PATH_SYNC_REQUEST = "/sync/request"
        const val PATH_PING = "/ping"
        
        const val KEY_ACTION = "action"
        const val KEY_DATA = "data"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_DEVICE_ID = "device_id"
        
        // 本地备份文件
        const val BACKUP_FILE = "sync_backup.json"
        const val PENDING_SYNC_FILE = "pending_sync.json"
    }
    
    /**
     * 初始化同步监听器
     * 兼容性：支持所有 Wear OS 2.0+ 设备
     */
    fun initializeSync() {
        // 监听数据变化
        dataClient.addListener { dataEvents ->
            scope.launch {
                handleDataEvents(dataEvents)
            }
        }
        
        // 监听消息
        messageClient.addListener { messageEvent ->
            scope.launch {
                handleMessage(messageEvent)
            }
        }
        
        // 检查连接状态
        scope.launch {
            checkConnectionStatus()
        }
    }
    
    /**
     * 同步任务到手机
     * 策略：先本地保存，后台异步同步，失败则加入待同步队列
     */
    suspend fun syncTaskToPhone(
        action: String,
        tag: String,
        priority: Int,
        timestamp: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val taskData = JSONObject().apply {
                put("action", action)
                put("tag", tag)
                put("priority", priority)
                put("timestamp", timestamp)
                put("device_id", getDeviceId())
            }
            
            // 1. 先保存到本地（确保数据不丢失）
            saveToLocalBackup(taskData)
            
            // 2. 尝试通过 Data API 同步
            val success = trySyncViaDataApi(PATH_SYNC_TASK, taskData)
            
            if (success) {
                Log.d(TAG, "✅ Task synced successfully via Data API")
                return@withContext true
            }
            
            // 3. Data API 失败，尝试 Message API（更快但不可靠）
            val messageSent = trySyncViaMessage(PATH_SYNC_TASK, taskData)
            
            if (!messageSent) {
                // 4. 都失败了，加入待同步队列
                addToPendingSync(taskData)
                Log.w(TAG, "⚠️ Task added to pending sync queue")
            }
            
            return@withContext messageSent
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Sync failed: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * 通过 Data API 同步
     * 优点：可靠、自动同步到所有设备、支持离线
     * 缺点：有延迟（几秒钟）
     */
    private suspend fun trySyncViaDataApi(path: String, data: JSONObject): Boolean {
        return try {
            val putDataReq = PutDataMapRequest.create(path).apply {
                dataMap.putString(KEY_DATA, data.toString())
                dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                dataMap.putString(KEY_DEVICE_ID, getDeviceId())
            }.asPutDataRequest().setUrgent() // 标记为紧急，优先传输
            
            val result = dataClient.putDataItem(putDataReq).await()
            Log.d(TAG, "Data API sync result: ${result.uri}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Data API failed: ${e.message}")
            false
        }
    }
    
    /**
     * 通过 Message API 同步
     * 优点：快速、实时
     * 缺点：不保证送达、需要设备在线
     */
    private suspend fun trySyncViaMessage(path: String, data: JSONObject): Boolean {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected nodes found")
                return false
            }
            
            var anySent = false
            nodes.forEach { node ->
                try {
                    messageClient.sendMessage(
                        node.id,
                        path,
                        data.toString().toByteArray()
                    ).await()
                    
                    Log.d(TAG, "Message sent to node: ${node.displayName}")
                    anySent = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send to ${node.displayName}: ${e.message}")
                }
            }
            
            anySent
        } catch (e: Exception) {
            Log.e(TAG, "Message API failed: ${e.message}")
            false
        }
    }
    
    /**
     * 批量同步今日记录
     * 适用于：应用启动时、手机端请求时
     */
    suspend fun syncTodayRecords(records: List<TaskRecord>): Boolean {
        return try {
            val jsonArray = JSONArray()
            records.forEach { record ->
                jsonArray.put(JSONObject().apply {
                    put("tag", record.tag)
                    put("priority", record.priority)
                    put("startTime", record.startTime)
                    put("endTime", record.endTime)
                })
            }
            
            val data = JSONObject().apply {
                put("records", jsonArray)
                put("timestamp", System.currentTimeMillis())
                put("device_id", getDeviceId())
            }
            
            // 记录较多，使用 Data API（支持更大数据）
            trySyncViaDataApi(PATH_SYNC_RECORDS, data)
        } catch (e: Exception) {
            Log.e(TAG, "Batch sync failed: ${e.message}")
            false
        }
    }
    
    /**
     * 请求手机端推送数据
     * 用途：手表端主动拉取手机端的配置、任务等
     */
    suspend fun requestSyncFromPhone() {
        try {
            val request = JSONObject().apply {
                put("type", "pull_config")
                put("timestamp", System.currentTimeMillis())
            }
            
            trySyncViaMessage(PATH_SYNC_REQUEST, request)
        } catch (e: Exception) {
            Log.e(TAG, "Request sync failed: ${e.message}")
        }
    }
    
    /**
     * 检查连接状态
     * 兼容性检查：确保设备支持 Wearable API
     */
    private suspend fun checkConnectionStatus() {
        try {
            val nodes = nodeClient.connectedNodes.await()
            
            if (nodes.isEmpty()) {
                Log.w(TAG, "⚠️ No paired devices found. Running in standalone mode.")
            } else {
                Log.d(TAG, "✅ Connected devices: ${nodes.size}")
                nodes.forEach { node ->
                    Log.d(TAG, "  - ${node.displayName} (${node.id})")
                }
                
                // 尝试同步待处理的数据
                processPendingSync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check connection: ${e.message}")
        }
    }
    
    /**
     * 处理接收到的数据事件
     */
    private fun handleDataEvents(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                Log.d(TAG, "Data changed: ${dataItem.uri.path}")
                
                when (dataItem.uri.path) {
                    PATH_SYNC_TASK -> {
                        // 接收到来自手机的任务
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val data = dataMap.getString(KEY_DATA)
                        handleReceivedTask(data)
                    }
                    PATH_SYNC_RECORDS -> {
                        // 手机端确认收到记录
                        Log.d(TAG, "Records synced successfully")
                    }
                }
            }
        }
    }
    
    /**
     * 处理接收到的消息
     */
    private fun handleMessage(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: ${messageEvent.path}")
        
        when (messageEvent.path) {
            PATH_PING -> {
                Log.d(TAG, "Ping received from phone")
            }
            PATH_SYNC_REQUEST -> {
                // 手机请求同步数据
                scope.launch {
                    // 这里需要从 ViewModel 获取数据
                    // 建议通过回调或 LiveData 实现
                }
            }
        }
    }
    
    /**
     * 处理接收到的任务（从手机推送）
     */
    private fun handleReceivedTask(data: String?) {
        try {
            val json = JSONObject(data ?: return)
            val action = json.getString("action")
            val tag = json.getString("tag")
            
            Log.d(TAG, "Received task: $action - $tag")
            
            // 这里需要通知 ViewModel 更新 UI
            // 建议使用 SharedFlow 或回调机制
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse received task: ${e.message}")
        }
    }
    
    /**
     * 保存到本地备份（防止数据丢失）
     */
    private fun saveToLocalBackup(data: JSONObject) {
        try {
            val file = File(context.filesDir, BACKUP_FILE)
            val backup = if (file.exists()) {
                JSONArray(file.readText())
            } else {
                JSONArray()
            }
            
            backup.put(data)
            
            // 只保留最近 100 条
            if (backup.length() > 100) {
                val trimmed = JSONArray()
                for (i in (backup.length() - 100) until backup.length()) {
                    trimmed.put(backup.get(i))
                }
                file.writeText(trimmed.toString())
            } else {
                file.writeText(backup.toString())
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save backup: ${e.message}")
        }
    }
    
    /**
     * 添加到待同步队列
     */
    private fun addToPendingSync(data: JSONObject) {
        try {
            val file = File(context.filesDir, PENDING_SYNC_FILE)
            val pending = if (file.exists()) {
                JSONArray(file.readText())
            } else {
                JSONArray()
            }
            
            pending.put(data)
            file.writeText(pending.toString())
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add to pending: ${e.message}")
        }
    }
    
    /**
     * 处理待同步队列（连接恢复时）
     */
    private suspend fun processPendingSync() {
        try {
            val file = File(context.filesDir, PENDING_SYNC_FILE)
            if (!file.exists()) return
            
            val pending = JSONArray(file.readText())
            Log.d(TAG, "Processing ${pending.length()} pending items")
            
            val failed = JSONArray()
            
            for (i in 0 until pending.length()) {
                val item = pending.getJSONObject(i)
                val success = trySyncViaDataApi(PATH_SYNC_TASK, item)
                
                if (!success) {
                    failed.put(item)
                }
            }
            
            // 更新待同步队列
            if (failed.length() > 0) {
                file.writeText(failed.toString())
            } else {
                file.delete()
                Log.d(TAG, "✅ All pending items synced")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process pending: ${e.message}")
        }
    }
    
    /**
     * 获取设备唯一ID
     */
    private fun getDeviceId(): String {
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        
        if (deviceId == null) {
            deviceId = "watch_${System.currentTimeMillis()}"
            prefs.edit().putString("device_id", deviceId).apply()
        }
        
        return deviceId
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        scope.cancel()
    }
}

/**
 * 任务记录数据类
 */
data class TaskRecord(
    val tag: String,
    val priority: Int,
    val startTime: Long,
    val endTime: Long
)



package massey.hamhuo.timetagger.presentation

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.tiles.TileService
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.atan2
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("time_tracker", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查是否需要重置并保存昨日日志
        checkAndResetDaily()

        setContent {
            MaterialTheme {
                App(
                    getLastTag = { getLastTag() },
                    onTagAdded = { priority, label -> appendEvent(priority, label) },
                    onTaskCompleted = { completeTask() },
                    onTagChanged = { updateLastTag(it) },
                    loadTodayRows = { loadTodayRows() }
                )
            }
        }
    }

    private fun checkAndResetDaily() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString("last_date", "")

        if (lastDate != today) {
            // 仅在 lastDate 不为空时保存昨日日志
            if (!lastDate.isNullOrEmpty()) {
                saveDailyLog(lastDate)
            }

            // 重置当前任务
            prefs.edit()
                .putString("last_tag", "")
                .putInt("last_priority", -1)
                .putString("last_date", today)
                .apply()
        }

    }

    private fun saveDailyLog(date: String?) {
        try {
            val key = "events_$date"
            val eventsJson = prefs.getString(key, "[]") ?: "[]"
            val arr = JSONArray(eventsJson)
            if (arr.length() == 0) return

            val externalDir = getExternalFilesDir(null) ?: return
            val logDir = File(externalDir, "logs")
            if (!logDir.exists()) logDir.mkdirs()

            val logFile = File(logDir, "$date.txt")
            val tf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val sb = StringBuilder()

            sb.appendLine("=== 时间日志 $date ===\n")

            for (i in 0 until arr.length()) {
                val cur = arr.getJSONObject(i)
                val start = cur.optLong("ts")
                val tag = cur.optString("tag")
                val priority = cur.optInt("priority", -1)
                val end = if (i + 1 < arr.length()) {
                    arr.getJSONObject(i + 1).optLong("ts")
                } else {
                    start + 60000 // 假设最后一个任务至少1分钟
                }

                val startStr = tf.format(Date(start))
                val endStr = tf.format(Date(end))
                val duration = formatDurationShort(end - start)
                val priorityName = when (priority) {
                    0 -> "P0-重要且紧急"
                    1 -> "P1-重要不紧急"
                    2 -> "P2-紧急不重要"
                    3 -> "P3-不重要不紧急"
                    else -> "未分类"
                }

                sb.appendLine("$startStr - $endStr ($duration)")
                sb.appendLine("[$priorityName] $tag")
                sb.appendLine()
            }

            logFile.writeText(sb.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun appendEvent(priority: Int, label: String) {
        val now = System.currentTimeMillis()
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
        val arr = JSONArray(prefs.getString("events_$day", "[]"))
        arr.put(JSONObject().put("ts", now).put("tag", label).put("priority", priority))
        prefs.edit()
            .putString("events_$day", arr.toString())
            .putString("last_tag", label)
            .putInt("last_priority", priority)
            .putString("last_date", day)
            .apply()
        notifyTileUpdate()
    }

    private fun completeTask() {
        prefs.edit()
            .putString("last_tag", "")
            .putInt("last_priority", -1)
            .apply()
        notifyTileUpdate()
    }

    private fun updateLastTag(label: String) {
        prefs.edit().putString("last_tag", label).apply()
        notifyTileUpdate()
    }

    private fun getLastTag(): Pair<Int, String> {
        val tag = prefs.getString("last_tag", "") ?: ""
        val priority = prefs.getInt("last_priority", -1)
        return Pair(priority, tag)
    }

    private fun loadTodayRows(): List<Row> {
        val key = "events_" + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        val arr = try { JSONArray(prefs.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
        if (arr.length() == 0) return emptyList()

        val rows = mutableListOf<Row>()
        for (i in 0 until arr.length()) {
            val cur = arr.getJSONObject(i)
            val start = cur.optLong("ts")
            val tag = cur.optString("tag")
            val priority = cur.optInt("priority", -1)
            val end = if (i + 1 < arr.length()) {
                arr.getJSONObject(i + 1).optLong("ts")
            } else {
                System.currentTimeMillis()
            }
            rows += Row(start, end, tag, priority)
        }
        return rows
    }

    private fun notifyTileUpdate() {
        try {
            TileService.getUpdater(this)
                .requestUpdate(TimeTagTileService::class.java)
        } catch (e: Exception) {
            // Tile 可能未启用，忽略错误
        }
    }
}

private data class Row(val start: Long, val end: Long, val tag: String, val priority: Int)

private data class PriorityConfig(
    val label: String,
    val color: Color,
    val description: String
)

private val priorityConfigs = mapOf(
    0 to PriorityConfig("P0", Color(0xFFEF5350), "重要且紧急"),
    1 to PriorityConfig("P1", Color(0xFF42A5F5), "重要不紧急"),
    2 to PriorityConfig("P2", Color(0xFFFFCA28), "紧急不重要"),
    3 to PriorityConfig("P3", Color(0xFF78909C), "不重要不紧急")
)

@Composable
private fun App(
    getLastTag: () -> Pair<Int, String>,
    onTagAdded: (Int, String) -> Unit,
    onTaskCompleted: () -> Unit,
    onTagChanged: (String) -> Unit,
    loadTodayRows: () -> List<Row>
) {
    var showHistory by remember { mutableStateOf(false) }

    if (showHistory) {
        HistoryScreen(
            rows = loadTodayRows(),
            onBack = { showHistory = false }
        )
    } else {
        PriorityCircleScreen(
            getLastTag = getLastTag,
            onTagAdded = onTagAdded,
            onTaskCompleted = onTaskCompleted,
            onTagChanged = onTagChanged,
            onClickTime = { showHistory = true }
        )
    }
}

@Composable
private fun PriorityCircleScreen(
    getLastTag: () -> Pair<Int, String>,
    onTagAdded: (Int, String) -> Unit,
    onTaskCompleted: () -> Unit,
    onTagChanged: (String) -> Unit,
    onClickTime: () -> Unit
) {
    val time by rememberTimeMinuteTicker()
    var currentTag by remember { mutableStateOf("") }
    var currentPriority by remember { mutableStateOf(-1) }
    var triggerShake by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 每次重新组合时同步状态
    LaunchedEffect(Unit) {
        val (priority, tag) = getLastTag()
        currentTag = tag
        currentPriority = priority
    }

    // 震动动画
    val shakeOffset by animateFloatAsState(
        targetValue = if (triggerShake) 1f else 0f,
        animationSpec = if (triggerShake) {
            repeatable(
                iterations = 6, // 往返 3 次
                animation = tween(durationMillis = 50, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(0)
        },
        finishedListener = { triggerShake = false }
    )

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.trim()
            if (!spoken.isNullOrEmpty() && currentPriority >= 0) {
                currentTag = spoken
                onTagAdded(currentPriority, spoken)
            }
        }
    }

    val editLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.trim()
            if (!spoken.isNullOrEmpty()) {
                currentTag = spoken
                onTagChanged(spoken)
            }
        }
    }

    fun startVoiceInput(priority: Int, isEdit: Boolean = false) {
        // 检查优先级规则（编辑模式除外）
        if (!isEdit && currentPriority >= 0 && priority > currentPriority) {
            triggerShake = true
            return
        }


        currentPriority = priority
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, if (isEdit) "修改任务名称" else "说出任务名称")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra("android.speech.extra.GET_AUDIO_FORMAT", "audio/AMR")
            putExtra("android.speech.extra.GET_AUDIO", true)
            flags = Intent.FLAG_ACTIVITY_NO_HISTORY
        }

        val packageManager = context.packageManager
        val activities = packageManager.queryIntentActivities(intent, 0)

        val preferredPackages = listOf(
            "com.samsung.android.bixby.agent",
            "com.samsung.android.svoice",
            "com.google.android.googlequicksearchbox",
            "com.google.android.voicesearch"
        )

        for (pkg in preferredPackages) {
            val found = activities.find { it.activityInfo.packageName == pkg }
            if (found != null) {
                intent.component = ComponentName(
                    found.activityInfo.packageName,
                    found.activityInfo.name
                )
                break
            }
        }

        if (isEdit) {
            editLauncher.launch(intent)
        } else {
            voiceLauncher.launch(intent)
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 优先级圆弧
        PriorityArcRing(
            onPriorityClick = { priority -> startVoiceInput(priority) }
        )

        // 中央显示区域
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    // shakeOffset: 0 -> 1 -> 0；(x-0.5)*40 => -20..0..20..0 的位移
                    translationX = if (triggerShake) (shakeOffset - 0.5f) * 40f else 0f
                }
        ) {
            Text(
                text = time,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onClickTime() }
            )

            if (currentTag.isNotEmpty() && currentPriority >= 0) {
                Spacer(Modifier.height(8.dp))
                val config = priorityConfigs[currentPriority]!!
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(config.color)
                )
                Spacer(Modifier.height(4.dp))

                // 双击完成，长按修改
                var lastClickTime by remember { mutableStateOf(0L) }

                Text(
                    text = currentTag,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                // 长按修改标签
                                startVoiceInput(currentPriority, isEdit = true)
                            },
                            onTap = {
                                // 检测双击
                                val now = System.currentTimeMillis()
                                if (now - lastClickTime < 500) {
                                    // 双击完成任务
                                    currentTag = ""
                                    currentPriority = -1
                                    onTaskCompleted()
                                }
                                lastClickTime = now
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun PriorityArcRing(
    onPriorityClick: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val dx = offset.x - centerX
                    val dy = offset.y - centerY
                    val distance = sqrt(dx * dx + dy * dy)

                    // 检查点击是否在圆环范围内
                    val outerRadius = size.width * 0.48f
                    val innerRadius = size.width * 0.35f

                    if (distance in innerRadius..outerRadius) {
                        // 计算角度 (0度在右侧，逆时针)
                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (angle < 0) angle += 360f

                        // 映射角度到优先级
                        val priority = when {
                            angle >= 315f || angle < 45f -> 1  // P1 右侧
                            angle >= 45f && angle < 135f -> 2   // P2 右下
                            angle >= 135f && angle < 225f -> 3  // P3 左下
                            else -> 0                           // P0 左上
                        }

                        onPriorityClick(priority)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val strokeWidth = 32f
            val radius = size.width * 0.415f
            val topLeft = Offset(centerX - radius, centerY - radius)
            val arcSize = Size(radius * 2, radius * 2)

            // P0: 左上 (225-315度)
            drawArc(
                color = priorityConfigs[0]!!.color,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // P1: 右上到右侧 (315-45度，跨越0度)
            drawArc(
                color = priorityConfigs[1]!!.color,
                startAngle = 315f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // P2: 右下 (45-135度)
            drawArc(
                color = priorityConfigs[2]!!.color,
                startAngle = 45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // P3: 左下 (135-225度)
            drawArc(
                color = priorityConfigs[3]!!.color,
                startAngle = 135f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun HistoryScreen(
    rows: List<Row>,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(rows) { r ->
            val startStr = tf.format(Date(r.start))
            val endStr = tf.format(Date(r.end))
            val durStr = formatDurationShort(r.end - r.start)
            val config = priorityConfigs[r.priority]

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                Text(
                    text = "$startStr - $endStr",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (config != null) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(config.color)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = "$durStr${if (r.tag.isNotEmpty()) "  ${r.tag}" else ""}",
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatDurationShort(ms: Long): String {
    val totalMinutes = ms / 60000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

@Composable
private fun rememberTimeMinuteTicker(): State<String> {
    val state = remember { mutableStateOf(currentTimeHHmm()) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = currentTimeHHmm()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    return state
}

private fun currentTimeHHmm(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
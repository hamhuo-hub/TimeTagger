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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.tiles.TileService
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("time_tracker", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(
                getLastTag = { getLastTag() },
                onTagAdded = { appendEvent(it) },
                onTagChanged = { updateLastTag(it) },
                loadTodayRows = { loadTodayRows() }
            )
        }
    }

    private fun appendEvent(label: String) {
        val now = System.currentTimeMillis()
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
        val arr = JSONArray(prefs.getString("events_$day", "[]"))
        arr.put(JSONObject().put("ts", now).put("tag", label))
        prefs.edit()
            .putString("events_$day", arr.toString())
            .putString("last_tag", label)
            .apply()
        notifyTileUpdate()
    }

    private fun updateLastTag(label: String) {
        prefs.edit().putString("last_tag", label).apply()
        notifyTileUpdate()
    }

    private fun getLastTag(): String = prefs.getString("last_tag", "") ?: ""

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
            val end = if (i + 1 < arr.length()) {
                arr.getJSONObject(i + 1).optLong("ts")
            } else {
                System.currentTimeMillis()
            }
            rows += Row(start, end, tag)
        }
        return rows
    }

    /** 通知 Tile 更新数据 */
    private fun notifyTileUpdate() {
        try {
            TileService.getUpdater(this)
                .requestUpdate(TimeTagTileService::class.java)
        } catch (e: Exception) {
            // Tile 可能未启用，忽略错误
        }
    }
}

private data class Row(val start: Long, val end: Long, val tag: String)

@Composable
private fun App(
    getLastTag: () -> String,
    onTagAdded: (String) -> Unit,
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
        MinimalVoiceScreen(
            getLastTag = getLastTag,
            onTagAdded = onTagAdded,
            onTagChanged = onTagChanged,
            onClickTime = { showHistory = true }
        )
    }
}

@Composable
private fun MinimalVoiceScreen(
    getLastTag: () -> String,
    onTagAdded: (String) -> Unit,
    onTagChanged: (String) -> Unit,
    onClickTime: () -> Unit
) {
    val time by rememberTimeMinuteTicker()
    var currentTag by remember { mutableStateOf(getLastTag()) }

    val addLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.trim()
            if (!spoken.isNullOrEmpty()) {
                currentTag = spoken
                onTagAdded(spoken)
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

    fun startVoiceInput(launch: (Intent) -> Unit) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        launch(intent)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = time,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onClickTime() }
            )
            Spacer(Modifier.height(8.dp))
            if (currentTag.isNotEmpty()) {
                Text(
                    text = currentTag,
                    fontSize = 20.sp,
                    modifier = Modifier.clickable {
                        startVoiceInput { editLauncher.launch(it) }
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = { startVoiceInput { addLauncher.launch(it) } }) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_btn_speak_now),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                Text(
                    text = "$startStr - $endStr",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (r.tag.isNotEmpty()) "$durStr  ${r.tag}" else durStr,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/** 简洁时长格式：1h 20m 或 15m */
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
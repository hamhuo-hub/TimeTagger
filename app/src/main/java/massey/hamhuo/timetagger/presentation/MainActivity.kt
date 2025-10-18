package massey.hamhuo.timetagger.presentation

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import massey.hamhuo.timetagger.presentation.components.PriorityArcRing
import massey.hamhuo.timetagger.presentation.components.rememberTimeMinuteTicker
import massey.hamhuo.timetagger.presentation.screens.HistoryScreen
import massey.hamhuo.timetagger.presentation.screens.PendingTasksScreen
import massey.hamhuo.timetagger.service.TimeTrackerService
import massey.hamhuo.timetagger.util.PriorityConfigs

/**
 * Main Activity
 */
class MainActivity : ComponentActivity() {
    
    private val _serviceState = MutableStateFlow<TimeTrackerService?>(null)
    private val serviceState: StateFlow<TimeTrackerService?> = _serviceState
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val serviceBinder = binder as TimeTrackerService.ServiceBinder
            _serviceState.value = serviceBinder.getService()
            isBound = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            _serviceState.value = null
            isBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start service
        TimeTrackerService.startService(this)
        
        // Bind service
        val intent = Intent(this, TimeTrackerService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        
        setContent {
            MaterialTheme {
                // Monitor service
                val trackerService by serviceState.collectAsState()
                
                if (trackerService != null) {
                    val viewModel: TimeTrackerViewModel = viewModel(
                        factory = TimeTrackerViewModelFactory(trackerService!!)
                    )
                    TimeTrackerApp(viewModel)
                } else {
                    // Loading
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Initializing...")
                    }
                }
            }
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Unbind service
        if (isBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: Exception) {
                // Ignore exception
            }
            isBound = false
        }
    }
    
    override fun onStart() {
        super.onStart()
        // Rebind service
        if (!isBound) {
            val intent = Intent(this, TimeTrackerService::class.java)
            try {
                bindService(intent, serviceConnection, BIND_AUTO_CREATE)
            } catch (e: Exception) {
                // Ignore exception
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Release resources
        if (isBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: Exception) {
                // Ignore exception
            }
            isBound = false
        }
        _serviceState.value = null
    }
}

/**
 * Main App
 */
@Composable
private fun TimeTrackerApp(viewModel: TimeTrackerViewModel) {
    var showHistory by remember { mutableStateOf(false) }
    var showPending by remember { mutableStateOf(false) }
    var pendingPriorityForAdd by remember { mutableIntStateOf(-1) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    
    // Voice launcher
    val voiceLauncherForPending = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (!spoken.isNullOrEmpty() && pendingPriorityForAdd >= 0) {
            viewModel.addPendingTask(pendingPriorityForAdd, spoken)
            pendingPriorityForAdd = -1
            refreshTrigger++
        }
    }
    
    // Back handler
    val onBackToMain = {
        showHistory = false
        showPending = false
        pendingPriorityForAdd = -1
    }
    
    when {
        showHistory -> {
            HistoryScreen(
                records = viewModel.getTodayRecords(),
                onBack = onBackToMain
            )
        }
        showPending -> {
            PendingTasksScreen(
                tasks = viewModel.getPendingTasks(),
                onBack = onBackToMain,
                onTaskSelected = { task ->
                    viewModel.startPendingTask(task)
                    showPending = false
                    TileUpdateManager.requestTileUpdate(context)
                },
                onAddTask = { priority ->
                    pendingPriorityForAdd = priority
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Task name")
                    }
                    voiceLauncherForPending.launch(intent)
                },
                refreshKey = refreshTrigger
            )
        }
        else -> {
            MainScreen(
                viewModel = viewModel,
                onClickTime = { 
                    if (!showHistory && !showPending) {
                        showHistory = true
                    }
                },
                onClickPending = { 
                    if (!showHistory && !showPending) {
                        showPending = true
                    }
                }
            )
        }
    }
}

/**
 * Main Screen
 */
@Composable
private fun MainScreen(
    viewModel: TimeTrackerViewModel,
    onClickTime: () -> Unit,
    onClickPending: () -> Unit
) {
    val time by rememberTimeMinuteTicker()
    var currentTag by remember { mutableStateOf("") }
    var currentPriority by remember { mutableIntStateOf(-1) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var pendingPriority by remember { mutableIntStateOf(-1) }
    val context = LocalContext.current
    
    // Rest state
    val isResting by viewModel.isResting.collectAsState()
    val restTimeLeft by viewModel.restTimeLeft.collectAsState()
    
    // Suggested task
    var suggestedTag by remember { mutableStateOf("") }
    var suggestedPriority by remember { mutableIntStateOf(-1) }
    
    // Refresh state
    LaunchedEffect(refreshTrigger) {
        val task = viewModel.getCurrentTask()
        currentTag = task.tag
        currentPriority = task.priority
        
        val suggested = viewModel.getSuggestedTask()
        suggestedTag = suggested.tag
        suggestedPriority = suggested.priority
    }
    
    // Voice launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (!spoken.isNullOrEmpty() && pendingPriority >= 0) {
            viewModel.addTask(pendingPriority, spoken)
            currentTag = spoken
            currentPriority = pendingPriority
            refreshTrigger++
            TileUpdateManager.requestTileUpdate(context)
        }
        pendingPriority = -1
    }
    
    // Edit launcher
    val editLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (!spoken.isNullOrEmpty()) {
            viewModel.updateCurrentTaskTag(spoken)
            currentTag = spoken
            refreshTrigger++
            TileUpdateManager.requestTileUpdate(context)
        }
    }
    
    // Start voice
    val startVoiceInput: (Int, Boolean) -> Unit = { priority, isEdit ->
        pendingPriority = priority
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, if (isEdit) "Edit task" else "Task name")
        }
        val launcher = if (isEdit) editLauncher else voiceLauncher
        launcher.launch(intent)
    }
    
    // Complete task
    val onCompleteTask: () -> Unit = {
        viewModel.completeTask()
        viewModel.stopTaskRest()
        currentTag = ""
        currentPriority = -1
        refreshTrigger++
        TileUpdateManager.requestTileUpdate(context)
    }
    
    // Stop rest
    val onStopRest: () -> Unit = {
        viewModel.stopTaskRest()
    }
    
    // Accept suggested
    val onAcceptSuggested: () -> Unit = {
        viewModel.acceptSuggestedTask()
        refreshTrigger++
        TileUpdateManager.requestTileUpdate(context)
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Priority ring
        PriorityArcRing(
            onPriorityClick = { priority -> startVoiceInput(priority, false) },
            onRestClick = {
                if (currentTag.isNotEmpty() && currentPriority >= 0) {
                    if (isResting) {
                        viewModel.stopTaskRest()
                    } else {
                        viewModel.startTaskRest()
                    }
                }
            }
        )
        
        // Center content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(40.dp))
            
            // Time display
            TimeDisplay(
                time = time,
                onClick = onClickTime,
                onLongPress = onClickPending
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Task display
            TaskDisplay(
                tag = currentTag,
                priority = currentPriority,
                suggestedTag = suggestedTag,
                suggestedPriority = suggestedPriority,
                isResting = isResting,
                restTimeLeft = restTimeLeft,
                onComplete = onCompleteTask,
                onLongPress = { startVoiceInput(currentPriority, true) },
                onRestClick = onStopRest,
                onAcceptSuggested = onAcceptSuggested
            )
        }
    }
}

/**
 * Time Display
 */
@Composable
private fun TimeDisplay(
    time: String,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    Text(
        text = time,
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
    )
}

/**
 * Task Display
 */
@Composable
private fun TaskDisplay(
    tag: String,
    priority: Int,
    suggestedTag: String,
    suggestedPriority: Int,
    isResting: Boolean,
    restTimeLeft: Long,
    onComplete: () -> Unit,
    onLongPress: () -> Unit,
    onRestClick: () -> Unit = {},
    onAcceptSuggested: () -> Unit = {}
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    
    Box(
        modifier = Modifier
            .height(70.dp)
            .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), 0) }
            .pointerInput(tag, priority, isResting) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // No swipe
                        if (isResting) return@detectDragGestures
                        
                        // Left swipe
                        if (dragAmount.x < 0) {
                            offsetX = (offsetX + dragAmount.x).coerceAtLeast(-200f)
                        } else if (offsetX < 0) {
                            // Right swipe
                            offsetX = (offsetX + dragAmount.x).coerceAtMost(0f)
                        }
                    },
                    onDragEnd = {
                        // No complete
                        if (isResting) {
                            offsetX = 0f
                            return@detectDragGestures
                        }
                        
                        // Complete task
                        if (offsetX < -80f && tag.isNotEmpty() && priority >= 0) {
                            onComplete()
                            offsetX = 0f
                        } else {
                            // Reset position
                            offsetX = 0f
                        }
                    }
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        if (tag.isNotEmpty() && priority >= 0) {
            if (isResting) {
                // Resting
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Box(
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .background(Color(0xFF4CAF50))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onRestClick() }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Spacer(Modifier.width(4.dp))
                            val minutes = (restTimeLeft / 1000 / 60).toInt()
                            val seconds = (restTimeLeft / 1000 % 60).toInt()
                            Text(
                                text = "%d:%02d".format(minutes, seconds),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                // Working
                val config = PriorityConfigs.get(priority)
                if (config != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(config.color)
                        )
                        Spacer(Modifier.height(8.dp))
                        
                        Text(
                            text = tag,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { onLongPress() }
                                )
                            }
                        )
                    }
                } else {
                    // Invalid priority
                    Text(
                        text = tag,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { onLongPress() }
                            )
                        }
                    )
                }
            }
        } else if (suggestedTag.isNotEmpty() && suggestedPriority >= 0) {
            // Suggested task
            val config = PriorityConfigs.get(suggestedPriority)
            if (config != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .pointerInput(suggestedTag) {
                            detectTapGestures(
                                onDoubleTap = {
                                    // Accept suggested
                                    onAcceptSuggested()
                                }
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    // Hint text
                    Text(
                        text = "next",
                        fontSize = 10.sp,
                        color = Color.Gray.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(4.dp))
                    
                    // Task text
                    Text(
                        text = suggestedTag,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray.copy(alpha = 0.6f)
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Priority dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(config.color.copy(alpha = 0.4f))
                    )
                }
            }
        } else {
            // Idle icon
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer circle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF666666))
                )
                // Check mark
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF000000))
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF666666))
                )
            }
        }
    }
}

package massey.hamhuo.timetagger.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import massey.hamhuo.timetagger.presentation.components.PriorityArcRing
import massey.hamhuo.timetagger.presentation.components.rememberTimeMinuteTicker
import massey.hamhuo.timetagger.presentation.screens.HistoryScreen
import massey.hamhuo.timetagger.presentation.screens.PendingTasksScreen
import massey.hamhuo.timetagger.util.PriorityConfigs

/**
 * 重构后的MainActivity
 * 职责：Activity生命周期管理和UI组合
 */
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                val viewModel: TimeTrackerViewModel = viewModel(
                    factory = TimeTrackerViewModelFactory(applicationContext)
                )
                
                TimeTrackerApp(viewModel)
            }
        }
    }
}

/**
 * 应用主入口
 */
@Composable
private fun TimeTrackerApp(viewModel: TimeTrackerViewModel) {
    var showHistory by remember { mutableStateOf(false) }
    var showPending by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    when {
        showHistory -> {
            HistoryScreen(
                records = viewModel.getTodayRecords(),
                onBack = { showHistory = false }
            )
        }
        showPending -> {
            PendingTasksScreen(
                tasks = viewModel.getPendingTasks(),
                onBack = { showPending = false },
                onTaskSelected = { task ->
                    viewModel.startPendingTask(task)
                    TileUpdateManager.requestTileUpdate(context)
                }
            )
        }
        else -> {
            MainScreen(
                viewModel = viewModel,
                onClickTime = { showHistory = true },
                onClickPending = { showPending = true }
            )
        }
    }
}

/**
 * 主屏幕
 */
@Composable
private fun MainScreen(
    viewModel: TimeTrackerViewModel,
    onClickTime: () -> Unit,
    onClickPending: () -> Unit
) {
    val time by rememberTimeMinuteTicker()
    var currentTag by remember { mutableStateOf("") }
    var currentPriority by remember { mutableStateOf(-1) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var pendingPriority by remember { mutableStateOf(-1) }
    val context = LocalContext.current
    
    // 休息状态
    val isResting by viewModel.isResting.collectAsState()
    val restTimeLeft by viewModel.restTimeLeft.collectAsState()
    
    // 语音输入管理器
    val voiceInputManager = remember { VoiceInputManager(context) }
    
    // 建议任务状态
    var suggestedTag by remember { mutableStateOf("") }
    var suggestedPriority by remember { mutableStateOf(-1) }
    
    // 刷新当前任务状态
    LaunchedEffect(refreshTrigger) {
        val task = viewModel.getCurrentTask()
        currentTag = task.tag
        currentPriority = task.priority
        
        val suggested = viewModel.getSuggestedTask()
        suggestedTag = suggested.tag
        suggestedPriority = suggested.priority
    }
    
    // 语音输入Launcher（添加任务）
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = voiceInputManager.extractSpokenText(result)
        if (!spoken.isNullOrEmpty() && pendingPriority >= 0) {
            viewModel.addTask(pendingPriority, spoken)
            currentTag = spoken
            currentPriority = pendingPriority
            refreshTrigger++
            TileUpdateManager.requestTileUpdate(context)
        }
        pendingPriority = -1
    }
    
    // 语音输入Launcher（编辑任务）
    val editLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = voiceInputManager.extractSpokenText(result)
        if (!spoken.isNullOrEmpty()) {
            viewModel.updateCurrentTaskTag(spoken)
            currentTag = spoken
            refreshTrigger++
            TileUpdateManager.requestTileUpdate(context)
        }
    }
    
    // 启动语音输入
    fun startVoiceInput(priority: Int, isEdit: Boolean = false) {
        pendingPriority = priority
        val launcher = if (isEdit) editLauncher else voiceLauncher
        voiceInputManager.startVoiceInput(launcher, isEdit)
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 优先级圆环（P3改为休息按钮）
        PriorityArcRing(
            onPriorityClick = { priority -> startVoiceInput(priority) },
            onRestClick = {
                // 只有在有任务时才能休息
                if (currentTag.isNotEmpty() && currentPriority >= 0) {
                    if (isResting) {
                        viewModel.stopTaskRest()
                    } else {
                        viewModel.startTaskRest()
                    }
                }
            }
        )
        
        // 中央内容区
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // 待办按钮
            PendingButton(
                count = viewModel.getPendingTaskCount(),
                onClick = onClickPending
            )
            
            Spacer(Modifier.height(16.dp))
            
            // 时间显示
            TimeDisplay(
                time = time,
                onClick = onClickTime
            )
            
            Spacer(Modifier.height(16.dp))
            
            // 任务标签或建议任务或空闲图标
            TaskDisplay(
                tag = currentTag,
                priority = currentPriority,
                suggestedTag = suggestedTag,
                suggestedPriority = suggestedPriority,
                isResting = isResting,
                restTimeLeft = restTimeLeft,
                onComplete = {
                    viewModel.completeTask()
                    viewModel.stopTaskRest() // 完成任务时停止休息
                    currentTag = ""
                    currentPriority = -1
                    refreshTrigger++
                    TileUpdateManager.requestTileUpdate(context)
                },
                onLongPress = {
                    startVoiceInput(currentPriority, isEdit = true)
                },
                onRestClick = {
                    viewModel.stopTaskRest() // 点击倒计时器提前结束休息
                },
                onAcceptSuggested = {
                    viewModel.acceptSuggestedTask()
                    refreshTrigger++
                    TileUpdateManager.requestTileUpdate(context)
                }
            )
        }
    }
}

/**
 * 待办按钮组件
 */
@Composable
private fun PendingButton(
    count: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.height(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x11000000))
                    .clickable { onClick() }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$count",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF888888)
                )
            }
        }
    }
}

/**
 * 时间显示组件
 */
@Composable
private fun TimeDisplay(
    time: String,
    onClick: () -> Unit
) {
    Text(
        text = time,
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
    )
}

/**
 * 任务显示组件
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
    var offsetX by remember { mutableStateOf(0f) }
    
    Box(
        modifier = Modifier
            .height(70.dp)
            .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), 0) }
            .pointerInput(tag, priority) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // 只允许左滑
                        if (dragAmount.x < 0) {
                            offsetX = (offsetX + dragAmount.x).coerceAtLeast(-200f)
                        } else if (offsetX < 0) {
                            // 右滑恢复
                            offsetX = (offsetX + dragAmount.x).coerceAtMost(0f)
                        }
                    },
                    onDragEnd = {
                        // 如果左滑超过80像素，完成任务
                        if (offsetX < -80f && tag.isNotEmpty() && priority >= 0) {
                            onComplete()
                            offsetX = 0f
                        } else {
                            // 否则恢复原位
                            offsetX = 0f
                        }
                    }
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        if (tag.isNotEmpty() && priority >= 0) {
            if (isResting) {
                // 休息中：显示椭圆形倒计时器（可点击提前结束）
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Box(
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .background(Color(0xFF4CAF50))
                            .clickable { onRestClick() }
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
                // 正常工作：显示任务标签
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
                    // 优先级无效（比如旧的P3数据），只显示文字
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
            // 有建议任务：显示半透明建议，支持双击确认
            val config = PriorityConfigs.get(suggestedPriority)
            if (config != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .pointerInput(suggestedTag) {
                            detectTapGestures(
                                onDoubleTap = {
                                    // 双击接受建议（开始待办队列第一个任务）
                                    onAcceptSuggested()
                                }
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    // 提示文字
                    Text(
                        text = "next",
                        fontSize = 10.sp,
                        color = Color.Gray.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(4.dp))
                    
                    // 半透明任务文字
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
                    
                    // 半透明优先级圆点
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(config.color.copy(alpha = 0.4f))
                    )
                }
            }
        } else {
            // 无任务：显示空闲图标
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "空闲",
                modifier = Modifier.size(32.dp),
                tint = Color(0xFF666666)
            )
        }
    }
}

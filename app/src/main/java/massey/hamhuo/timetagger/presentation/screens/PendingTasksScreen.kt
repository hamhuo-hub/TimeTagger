package massey.hamhuo.timetagger.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import massey.hamhuo.timetagger.data.model.PendingTask
import massey.hamhuo.timetagger.util.PriorityConfigs

/**
 * Pending Tasks Screen
 */
@Composable
fun PendingTasksScreen(
    tasks: List<PendingTask>,
    onBack: () -> Unit,
    onTaskSelected: (PendingTask) -> Unit = {},
    onAddTask: (Int) -> Unit = {},
    refreshKey: Int = 0
) {
    BackHandler { onBack() }
    
    // Refresh trigger
    LaunchedEffect(refreshKey) {
        // Recompose for latest
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp)
                .padding(top = 40.dp, bottom = 12.dp), // Top button space
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = "Planning",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(tasks.sortedWith(compareBy({ it.priority }, { it.addTime }))) { task ->
                val config = PriorityConfigs.get(task.priority)
                if (config != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            // Start task
                            onTaskSelected(task)
                            onBack()
                        }
                            .background(Color(0x11FFFFFF))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(config.color)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = task.tag,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // Bottom motto
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "会选择才会有时间",
                    fontSize = 12.sp,
                    color = Color(0xFF888888),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
        
        // Top arcs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val strokeWidth = 32f
            val radius = size.width * 0.415f
            val topLeft = Offset(centerX - radius, centerY - radius)
            val arcSize = Size(radius * 2, radius * 2)
            
            // P2 arc
            drawArc(
                color = PriorityConfigs.get(2)!!.color,
                startAngle = -120f,
                sweepAngle = 30f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // P1 arc
            drawArc(
                color = PriorityConfigs.get(1)!!.color,
                startAngle = -90f,
                sweepAngle = 30f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        
        // Top click areas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // P2 click area
            Box(
                modifier = Modifier
                    .weight(0.2f)
                    .fillMaxHeight()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onAddTask(2) }
            )
            
            // P1 click area
            Box(
                modifier = Modifier
                    .weight(0.2f)
                    .fillMaxHeight()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onAddTask(1) }
            )
        }
    }
}


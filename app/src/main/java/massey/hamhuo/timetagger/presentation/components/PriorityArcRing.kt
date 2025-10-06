package massey.hamhuo.timetagger.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import massey.hamhuo.timetagger.util.PriorityConfigs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 优先级弧形环组件
 * 显示三个优先级区域和一个休息按钮
 */
@Composable
fun PriorityArcRing(
    onPriorityClick: (Int) -> Unit,
    onRestClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val dx = offset.x - centerX
                    val dy = offset.y - centerY
                    val distance = sqrt(dx * dx + dy * dy)
                    
                    val outerRadius = size.width * 0.48f
                    val innerRadius = size.width * 0.35f
                    
                    if (distance in innerRadius..outerRadius) {
                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (angle < 0) angle += 360f
                        
                        when {
                            angle >= 315f || angle < 45f -> onPriorityClick(1)  // 右侧 P1
                            angle >= 45f && angle < 135f -> onPriorityClick(2)  // 下方 P2
                            angle >= 135f && angle < 225f -> onRestClick()      // 左侧 休息按钮
                            else -> onPriorityClick(0)                           // 上方 P0
                        }
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
            
            // P0 - 左上 (225° - 315°)
            drawArc(
                color = PriorityConfigs.get(0)!!.color,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // P1 - 右上 (315° - 45°)
            drawArc(
                color = PriorityConfigs.get(1)!!.color,
                startAngle = 315f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // P2 - 右下 (45° - 135°)
            drawArc(
                color = PriorityConfigs.get(2)!!.color,
                startAngle = 45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // 休息按钮 - 左下 (135° - 225°)，使用灰色
            drawArc(
                color = androidx.compose.ui.graphics.Color(0xFF78909C),
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


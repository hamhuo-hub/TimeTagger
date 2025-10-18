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
 * Priority Arc Ring
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
                            angle >= 330f || angle < 90f -> onPriorityClick(1)   // P1 (330° - 90°)
                            angle >= 90f && angle < 210f -> onPriorityClick(2)   // P2 (90° - 210°)
                            else -> onRestClick()                                  // Rest (210° - 330°)
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
            
            // P1 arc (330° - 90°, 120°)
            drawArc(
                color = PriorityConfigs.get(1)!!.color,
                startAngle = 330f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // P2 arc (90° - 210°, 120°)
            drawArc(
                color = PriorityConfigs.get(2)!!.color,
                startAngle = 90f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // Rest button (210° - 330°, 120°)
            drawArc(
                color = androidx.compose.ui.graphics.Color(0xFF78909C),
                startAngle = 210f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}


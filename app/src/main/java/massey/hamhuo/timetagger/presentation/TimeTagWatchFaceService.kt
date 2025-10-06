package massey.hamhuo.timetagger.presentation

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.view.SurfaceHolder
import androidx.compose.ui.graphics.toArgb
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.TapType
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import massey.hamhuo.timetagger.data.storage.CrossProcessDataReader
import massey.hamhuo.timetagger.util.DateFormatter
import massey.hamhuo.timetagger.util.PriorityConfigs
import java.time.ZonedDateTime

/**
 * TimeTag 表盘服务
 * 显示时间和当前任务
 */
class TimeTagWatchFaceService : WatchFaceService() {
    
    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        
        val renderer = TimeTagRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            currentUserStyleRepository = currentUserStyleRepository,
            canvasType = CanvasType.HARDWARE
        )
        
        return WatchFace(
            watchFaceType = WatchFaceType.DIGITAL,
            renderer = renderer
        ).apply {
            // 设置点击监听器
            setTapListener(object : WatchFace.TapListener {
                override fun onTapEvent(tapType: Int, tapEvent: TapEvent, complicationSlot: androidx.wear.watchface.ComplicationSlot?) {
                    if (tapType == TapType.UP) {
                        // 点击表盘时打开应用
                        val intent = Intent(applicationContext, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            applicationContext,
                            0,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        try {
                            pendingIntent.send()
                        } catch (e: Exception) {
                            // 忽略异常
                        }
                    }
                }
            })
        }
    }
}

/**
 * 表盘渲染器
 */
private class TimeTagRenderer(
    private val context: android.content.Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int
) : androidx.wear.watchface.Renderer.CanvasRenderer(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    16L // 16ms = 60fps
) {
    
    // 使用跨进程数据读取器
    private val dataReader: CrossProcessDataReader? by lazy {
        try {
            CrossProcessDataReader(context)
        } catch (e: Exception) {
            null
        }
    }
    
    // 画笔
    private val backgroundPaint = Paint().apply {
        color = Color.BLACK
    }
    
    private val timePaint = TextPaint().apply {
        isAntiAlias = true
        textSize = 60f
        color = Color.WHITE
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    
    private val taskPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = 24f
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    
    private val labelPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = 18f
        color = Color.WHITE
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    
    private val priorityBgPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val hintPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = 20f
        color = Color.GRAY
        textAlign = Paint.Align.CENTER
    }
    
    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        // 绘制背景
        canvas.drawRect(bounds, backgroundPaint)
        
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        
        // 获取当前时间（带容错）
        val timeStr = try {
            DateFormatter.getCurrentTime()
        } catch (e: Exception) {
            String.format("%02d:%02d", zonedDateTime.hour, zonedDateTime.minute)
        }
        
        // 获取当前任务（使用跨进程读取器，独立于主应用进程）
        val currentTask = try {
            dataReader?.getCurrentTask() ?: massey.hamhuo.timetagger.data.model.CurrentTask.empty()
        } catch (e: Exception) {
            massey.hamhuo.timetagger.data.model.CurrentTask.empty()
        }
        
        // 绘制时间
        canvas.drawText(timeStr, centerX, centerY - 20f, timePaint)
        
        // 绘制任务信息
        if (currentTask.tag.isNotEmpty() && currentTask.priority >= 0) {
            // 有任务时显示任务信息
            val config = PriorityConfigs.get(currentTask.priority)
            if (config != null) {
                // 绘制优先级标签背景
                val labelText = "P${currentTask.priority} ${config.description}"
                val labelWidth = labelPaint.measureText(labelText) + 40f
                val labelHeight = 36f
                val labelTop = centerY + 30f
                
                priorityBgPaint.color = config.color.toArgb()
                val labelRect = RectF(
                    centerX - labelWidth / 2,
                    labelTop,
                    centerX + labelWidth / 2,
                    labelTop + labelHeight
                )
                canvas.drawRoundRect(labelRect, 18f, 18f, priorityBgPaint)
                
                // 绘制优先级标签文字
                canvas.drawText(
                    labelText,
                    centerX,
                    labelTop + labelHeight / 2 + 6f,
                    labelPaint
                )
                
                // 绘制任务名称
                val taskY = labelTop + labelHeight + 30f
                drawMultilineText(
                    canvas,
                    currentTask.tag,
                    centerX,
                    taskY,
                    taskPaint,
                    bounds.width() - 80f,
                    2
                )
            }
        } else {
            // 无任务时显示提示
            canvas.drawText("点击进入应用", centerX, centerY + 50f, hintPaint)
        }
        
        // 在环境模式下降低亮度
        if (renderParameters.drawMode == androidx.wear.watchface.DrawMode.AMBIENT) {
            canvas.drawColor(Color.argb(200, 0, 0, 0))
        }
    }
    
    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        // 可选：实现高亮效果
    }
    
    /**
     * 绘制多行文本
     */
    private fun drawMultilineText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        paint: TextPaint,
        maxWidth: Float,
        maxLines: Int
    ) {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        
        // 绘制最多 maxLines 行
        val linesToDraw = lines.take(maxLines)
        val lineHeight = paint.textSize + 8f
        var currentY = y
        
        for (line in linesToDraw) {
            val displayText = if (line == linesToDraw.last() && lines.size > maxLines) {
                line.take(20) + "..."
            } else {
                line
            }
            canvas.drawText(displayText, x, currentY, paint)
            currentY += lineHeight
        }
    }
}


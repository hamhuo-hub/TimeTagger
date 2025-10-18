package massey.hamhuo.timetagger.watchface

import android.content.Context
import android.graphics.*
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import java.time.ZonedDateTime
import androidx.core.graphics.toColorInt

/**
 * Watch Face Renderer
 */
class TimeTagWatchFaceRenderer(
    context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository
) : Renderer.CanvasRenderer2<TimeTagWatchFaceRenderer.TimeTagSharedAssets>(
    surfaceHolder = surfaceHolder,
    currentUserStyleRepository = currentUserStyleRepository,
    watchState = watchState,
    canvasType = CanvasType.HARDWARE,
    interactiveDrawModeUpdateDelayMillis = 16L,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false
) {

    // Shared assets
    class TimeTagSharedAssets : SharedAssets {
        override fun onDestroy() {}
    }

    override suspend fun createSharedAssets(): TimeTagSharedAssets {
        return TimeTagSharedAssets()
    }

    // Task reader
    private val taskReader = CurrentTaskReader(context)
    
    // Priority config
    private data class PriorityConfig(
        val color: Int,
        val labelCn: String,
        val labelEn: String
    )
    
    private val priorityConfigs = mapOf(
        1 to PriorityConfig(0xFF42A5F5.toInt(), "核心", "Core"),
        2 to PriorityConfig(0xFFFFCA28.toInt(), "短期", "Urgent")
    )

    // Paint
    private val timePaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f * 2          // Tile: 36sp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val tagPaint = Paint().apply {
        color = Color.WHITE
        textSize = 16f * 2          // Tile: 14sp
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val pLabelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 14f * 2          // Tile: 11sp
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val cnLabelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 6f * 2          // Tile: 10sp
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
    }

    private val enLabelPaint = Paint().apply {
        color = "#EEEEEE".toColorInt()
        textSize = 6f * 2           // Tile: 9sp
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
    }
    
    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val mottoPaint = Paint().apply {
        color = "#AAAAAA".toColorInt()
        textSize = 10f * 2
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    
    // Motto list (random display)
    private val mottos = listOf(
        listOf("Start where you are", "Do what you can"),
        listOf("Action creates meaning", "Not the other way around"),
        listOf("Every moment matters")
    )

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: TimeTagSharedAssets
    ) {
        // Background
        canvas.drawColor(Color.BLACK)

        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()

        // Draw time
        val timeText = String.format(
            "%02d:%02d",
            zonedDateTime.hour,
            zonedDateTime.minute
        )
        canvas.drawText(timeText, centerX, centerY - 60f, timePaint)

        // Read current task
        val currentTask = taskReader.getCurrentTask()

        if (currentTask.tag.isNotEmpty() && currentTask.priority >= 0) {
            val config = priorityConfigs[currentTask.priority]
            if (config != null) {
                // Draw priority label
                drawPriorityLabel(canvas, centerX, centerY + 10f, currentTask.priority, config)
                
                // Draw task name
                canvas.drawText(currentTask.tag, centerX, centerY + 75f, tagPaint)
            }
        } else {
            // No task hint
            tagPaint.color = "#888888".toColorInt()
            canvas.drawText("Tap to start", centerX, centerY + 10f, tagPaint)
        }
        
        // Draw motto
        val seed = zonedDateTime.hour * 60 + zonedDateTime.minute
        val mottoIndex = seed % mottos.size
        val currentMotto = mottos[mottoIndex]
        
        // Calculate position
        val lineCount = currentMotto.size
        val lineHeight = 24f
        var mottoY = bounds.bottom - 60f + (2 - lineCount) * lineHeight / 2
        
        // Draw lines
        currentMotto.forEach { line ->
            canvas.drawText(line, centerX, mottoY, mottoPaint)
            mottoY += lineHeight
        }
    }
    
    // Draw priority label
    private fun drawPriorityLabel(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        priority: Int,
        config: PriorityConfig
    ) {
        // Calculate size
        val pText = "P$priority"
        val pWidth = pLabelPaint.measureText(pText)
        val cnWidth = cnLabelPaint.measureText(config.labelCn)
        val enWidth = enLabelPaint.measureText(config.labelEn)
        val labelWidth = pWidth + 12f + maxOf(cnWidth, enWidth)
        val labelHeight = 56f
        
        // Background
        val rect = RectF(
            centerX - labelWidth / 2 - 16f,
            centerY - labelHeight / 2,
            centerX + labelWidth / 2 + 16f,
            centerY + labelHeight / 2
        )
        
        backgroundPaint.color = config.color
        canvas.drawRoundRect(rect, 24f, 24f, backgroundPaint)
        
        // P label
        val textX = centerX - labelWidth / 2 + 8f
        canvas.drawText(pText, textX, centerY + 8f, pLabelPaint)
        
        // CN and EN labels
        val descX = textX + pWidth + 12f
        canvas.drawText(config.labelCn, descX, centerY - 2f, cnLabelPaint)
        canvas.drawText(config.labelEn, descX, centerY + 14f, enLabelPaint)
    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: TimeTagSharedAssets
    ) {
        // Highlight layer
    }
}


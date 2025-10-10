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
 * 表盘渲染器
 * 使用与 Tile 相同的设计语言
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

    // 共享资源
    class TimeTagSharedAssets : SharedAssets {
        override fun onDestroy() {}
    }

    override suspend fun createSharedAssets(): TimeTagSharedAssets {
        return TimeTagSharedAssets()
    }

    // 数据读取器
    private val taskReader = CurrentTaskReader(context)
    
    // 优先级配置（与 Tile 一致）
    private data class PriorityConfig(
        val color: Int,
        val labelCn: String,
        val labelEn: String
    )
    
    private val priorityConfigs = mapOf(
        0 to PriorityConfig(0xFFEF5350.toInt(), "突发", "Important & Urgent"),
        1 to PriorityConfig(0xFF42A5F5.toInt(), "核心", "Core"),
        2 to PriorityConfig(0xFFFFCA28.toInt(), "短期", "Urgent")
    )

    // 画笔（与 Tile 尺寸一致，表盘放大2倍）
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
        textSize = 10f * 2           // 较小的字体
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    
    // 格言列表（随机显示）
    private val mottos = listOf(
        listOf("不要好高骛远","把力所能及的事做好"),
        listOf("很多事不是因为有意义才去做", "而是做了才有意义"),
        listOf("正在做的每一件事都有意义")
    )

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: TimeTagSharedAssets
    ) {
        // 背景
        canvas.drawColor(Color.BLACK)

        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()

        // 绘制时间（与 Tile 一致）
        val timeText = String.format(
            "%02d:%02d",
            zonedDateTime.hour,
            zonedDateTime.minute
        )
        canvas.drawText(timeText, centerX, centerY - 60f, timePaint)

        // 读取当前任务
        val currentTask = taskReader.getCurrentTask()

        if (currentTask.tag.isNotEmpty() && currentTask.priority >= 0) {
            val config = priorityConfigs[currentTask.priority]
            if (config != null) {
                // 绘制优先级标签（带圆角背景，与 Tile 一致）
                drawPriorityLabel(canvas, centerX, centerY + 10f, currentTask.priority, config)
                
                // 绘制任务名称
                canvas.drawText(currentTask.tag, centerX, centerY + 75f, tagPaint)
            }
        } else {
            // 无任务提示
            tagPaint.color = "#888888".toColorInt()
            canvas.drawText("Tap to start", centerX, centerY + 10f, tagPaint)
        }
        
        // 绘制底部格言（随机切换，每分钟更新）
        // 使用当前小时+分钟作为随机种子，保证同一分钟内显示相同格言
        val seed = zonedDateTime.hour * 60 + zonedDateTime.minute
        val mottoIndex = seed % mottos.size
        val currentMotto = mottos[mottoIndex]
        
        // 动态计算格言起始位置（支持1-2行）
        val lineCount = currentMotto.size
        val lineHeight = 24f
        var mottoY = bounds.bottom - 60f + (2 - lineCount) * lineHeight / 2
        
        // 绘制所有行
        currentMotto.forEach { line ->
            canvas.drawText(line, centerX, mottoY, mottoPaint)
            mottoY += lineHeight
        }
    }
    
    /**
     * 绘制优先级标签（模仿 Tile 的圆角背景设计）
     */
    private fun drawPriorityLabel(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        priority: Int,
        config: PriorityConfig
    ) {
        // 计算标签尺寸
        val pText = "P$priority"
        val pWidth = pLabelPaint.measureText(pText)
        val cnWidth = cnLabelPaint.measureText(config.labelCn)
        val enWidth = enLabelPaint.measureText(config.labelEn)
        val labelWidth = pWidth + 12f + maxOf(cnWidth, enWidth)
        val labelHeight = 56f  // 高度
        
        // 圆角矩形背景
        val rect = RectF(
            centerX - labelWidth / 2 - 16f,
            centerY - labelHeight / 2,
            centerX + labelWidth / 2 + 16f,
            centerY + labelHeight / 2
        )
        
        backgroundPaint.color = config.color
        canvas.drawRoundRect(rect, 24f, 24f, backgroundPaint)
        
        // P 标签
        val textX = centerX - labelWidth / 2 + 8f
        canvas.drawText(pText, textX, centerY + 8f, pLabelPaint)
        
        // 中文和英文标签（垂直排列）
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
        // 高亮层（可选）
    }
}


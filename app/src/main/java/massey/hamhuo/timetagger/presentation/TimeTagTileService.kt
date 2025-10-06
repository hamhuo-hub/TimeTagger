package massey.hamhuo.timetagger.presentation

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.*
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.protolayout.TimelineBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import massey.hamhuo.timetagger.data.repository.TimeTrackerRepository
import massey.hamhuo.timetagger.util.DateFormatter
import java.util.*

class TimeTagTileService : TileService() {

    private val repository by lazy {
        TimeTrackerRepository(applicationContext)
    }

    // 优先级配置
    private data class PriorityConfig(
        val color: Int,
        val labelCn: String,
        val labelEn: String
    )

    private val priorityConfigs = mapOf(
        0 to PriorityConfig(0xFFEF5350.toInt(), "突发", "Important & Urgent"),
        1 to PriorityConfig(0xFF42A5F5.toInt(), "核心", "Core"),
        2 to PriorityConfig(0xFFFFCA28.toInt(), "短期", "Urgent"),
//        3 to PriorityConfig(0xFF78909C.toInt(), "休息", "Neither")
    )

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return try {
            val timeStr = DateFormatter.getCurrentTime()
            val currentTask = repository.getCurrentTask()
            val lastTag = currentTask.tag
            val lastPriority = currentTask.priority

            val root = tileLayout(timeStr, lastTag, lastPriority)

            val tile = TileBuilders.Tile.Builder()
                .setResourcesVersion(RES_VERSION)
                .setFreshnessIntervalMillis(60_000)
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(root)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()

            Futures.immediateFuture(tile)
        } catch (e: Exception) {
            e.printStackTrace()
            // 返回一个简单的默认 Tile
            val defaultRoot = createDefaultTile()
            val defaultTile = TileBuilders.Tile.Builder()
                .setResourcesVersion(RES_VERSION)
                .setFreshnessIntervalMillis(60_000)
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(defaultRoot)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
            Futures.immediateFuture(defaultTile)
        }
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return try {
            val res = ResourceBuilders.Resources.Builder()
                .setVersion(RES_VERSION)
                .build()
            Futures.immediateFuture(res)
        } catch (e: Exception) {
            e.printStackTrace()
            val defaultRes = ResourceBuilders.Resources.Builder()
                .setVersion(RES_VERSION)
                .build()
            Futures.immediateFuture(defaultRes)
        }
    }

    private fun tileLayout(timeStr: String, lastTag: String, priority: Int): LayoutElementBuilders.LayoutElement {
        val clickable = Clickable.Builder()
            .setId("open_app")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setClassName(MainActivity::class.java.name)
                            .setPackageName(applicationContext.packageName)
                            .build()
                    )
                    .build()
            )
            .build()

        // 时间文本
        val timeText = Text.Builder()
            .setText(timeStr)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(36f))
                    .setWeight(FONT_WEIGHT_BOLD)
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .build()
            )
            .build()

        val column = Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(timeText)
            .addContent(Spacer.Builder().setHeight(dp(8f)).build())

        if (lastTag.isNotEmpty() && priority >= 0) {
            // 有任务时显示优先级标签和任务名
            val config = priorityConfigs[priority]
            if (config != null) {
                // 优先级标签（带背景色的小标签）
                val priorityLabel = createPriorityLabel(priority, config)
                column.addContent(priorityLabel)
                column.addContent(Spacer.Builder().setHeight(dp(6f)).build())

                // 任务名称
                val taskText = Text.Builder()
                    .setText(lastTag)
                    .setMaxLines(2)
                    .setFontStyle(
                        FontStyle.Builder()
                            .setSize(sp(14f))
                            .setColor(argb(0xFFFFFFFF.toInt()))
                            .build()
                    )
                    .build()
                column.addContent(taskText)
            }
        } else {
            // 无任务时显示提示
            val hintText = Text.Builder()
                .setText("Tap to start")
                .setFontStyle(
                    FontStyle.Builder()
                        .setSize(sp(14f))
                        .setColor(argb(0xFF888888.toInt()))
                        .build()
                )
                .build()
            column.addContent(hintText)
        }

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setPadding(
                        Padding.Builder()
                            .setAll(dp(12f))
                            .build()
                    )
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(0xFF000000.toInt()))
                            .build()
                    )
                    .build()
            )
            .addContent(column.build())
            .build()
    }

    private fun createPriorityLabel(priority: Int, config: PriorityConfig): LayoutElementBuilders.LayoutElement {
        // P0/P1/P2/P3 标签
        val pLabel = Text.Builder()
            .setText("P$priority")
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(11f))
                    .setWeight(FONT_WEIGHT_BOLD)
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .build()
            )
            .build()

        // 中文描述
        val cnLabel = Text.Builder()
            .setText(config.labelCn)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(10f))
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .build()
            )
            .build()

        // 英文描述
        val enLabel = Text.Builder()
            .setText(config.labelEn)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(9f))
                    .setColor(argb(0xFFEEEEEE.toInt()))
                    .build()
            )
            .build()

        // 水平排列：P标签 + 描述文字
        val labelRow = Row.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(pLabel)
            .addContent(Spacer.Builder().setWidth(dp(6f)).build())
            .addContent(
                Column.Builder()
                    .setWidth(wrap())
                    .setHeight(wrap())
                    .addContent(cnLabel)
                    .addContent(enLabel)
                    .build()
            )
            .build()

        // 带圆角背景的容器
        return Box.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        Padding.Builder()
                            .setStart(dp(8f))
                            .setEnd(dp(8f))
                            .setTop(dp(4f))
                            .setBottom(dp(4f))
                            .build()
                    )
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(config.color))
                            .setCorner(
                                Corner.Builder()
                                    .setRadius(dp(12f))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(labelRow)
            .build()
    }

    private fun createDefaultTile(): LayoutElementBuilders.LayoutElement {
        val clickable = Clickable.Builder()
            .setId("open_app")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setClassName(MainActivity::class.java.name)
                            .setPackageName(applicationContext.packageName)
                            .build()
                    )
                    .build()
            )
            .build()

        val text = Text.Builder()
            .setText("Tap to open")
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(16f))
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .build()
            )
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(0xFF000000.toInt()))
                            .build()
                    )
                    .build()
            )
            .addContent(
                Column.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(text)
                    .build()
            )
            .build()
    }

    companion object {
        private const val RES_VERSION = "1"
    }
}
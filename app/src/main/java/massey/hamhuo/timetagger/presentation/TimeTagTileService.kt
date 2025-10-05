package massey.hamhuo.timetagger.presentation

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.protolayout.TimelineBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.text.SimpleDateFormat
import java.util.*

/**
 * 显示当前时间（HH:mm）与最近标签（last_tag），点击 Tile 打开应用。
 * 与应用通过 SharedPreferences("time_tracker") 同步。
 */
class TimeTagTileService : TileService() {

    private val prefs by lazy {
        applicationContext.getSharedPreferences("time_tracker", MODE_PRIVATE)
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
        val lastTag = prefs.getString("last_tag", "") ?: ""

        val root = tileLayout(timeStr, lastTag)

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(60_000) // 1 min
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

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val res = ResourceBuilders.Resources.Builder()
            .setVersion(RES_VERSION)
            .build()
        return Futures.immediateFuture(res)
    }

    private fun tileLayout(timeStr: String, lastTag: String): LayoutElementBuilders.LayoutElement {
        // 点击打开应用
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

        // 时间文本（大号、加粗）
        val timeText = Text.Builder()
            .setText(timeStr)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(32f))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .build()
            )
            .build()

        // 标签文本（小号）
        val tagText = Text.Builder()
            .setText(if (lastTag.isEmpty()) "Tap to start" else lastTag)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(16f))
                    .setColor(argb(if (lastTag.isEmpty()) 0xFF888888.toInt() else 0xFFFFFFFF.toInt()))
                    .build()
            )
            .build()

        // 垂直布局
        val column = Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(timeText)
            .addContent(Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(tagText)
            .build()

        // 根容器
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setPadding(
                        Padding.Builder()
                            .setAll(dp(16f))
                            .build()
                    )
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(0xFF000000.toInt()))
                            .build()
                    )
                    .build()
            )
            .addContent(column)
            .build()
    }

    companion object {
        private const val RES_VERSION = "1"
    }
}
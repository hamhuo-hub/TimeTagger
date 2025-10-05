package massey.hamhuo.timetagger.presentation

import android.content.Context
import androidx.wear.tiles.TileService

/**
 * Tile更新管理器
 * 负责通知Tile服务更新
 */
object TileUpdateManager {
    
    /**
     * 请求更新Tile
     */
    fun requestTileUpdate(context: Context) {
        try {
            TileService.getUpdater(context)
                .requestUpdate(TimeTagTileService::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}


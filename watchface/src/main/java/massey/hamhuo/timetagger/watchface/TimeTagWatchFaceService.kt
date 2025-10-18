package massey.hamhuo.timetagger.watchface

import android.app.PendingIntent
import android.content.Intent
import android.view.SurfaceHolder
import androidx.wear.watchface.*
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema


class TimeTagWatchFaceService : WatchFaceService() {

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        
        // 创建渲染器
        val renderer = TimeTagWatchFaceRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            currentUserStyleRepository = currentUserStyleRepository
        )
        
        // 创建表盘
        return WatchFace(
            watchFaceType = WatchFaceType.DIGITAL,
            renderer = renderer
        ).apply {
            // 设置点击动作：打开主应用
            setTapListener(object : WatchFace.TapListener {
                override fun onTapEvent(
                    tapType: Int,
                    tapEvent: TapEvent,
                    complicationSlot: ComplicationSlot?
                ) {
                    if (tapType == TapType.UP) {
                        openMainApp()
                    }
                }
            })
        }
    }
    
    /**
     * 打开主应用
     */
    private fun openMainApp() {
        try {
            val intent = Intent().apply {
                setClassName(
                    "massey.hamhuo.timetagger",
                    "massey.hamhuo.timetagger.presentation.MainActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            // 主应用未安装，忽略
        }
    }
}


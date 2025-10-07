package massey.hamhuo.timetagger.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import massey.hamhuo.timetagger.data.repository.TimeTrackerRepository

/**
 * 休息结束闹钟接收器
 */
class RestAlarmReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REST_FINISHED) {
            val repository = TimeTrackerRepository(context)
            
            // 记录5分钟休息时间
            repository.addRestTimeToCurrentTask(5 * 60 * 1000L)
            
            // 清除休息开始时间
            repository.clearRestStartTime()
            
            // 振动提醒
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (vibrator.hasVibrator()) {
                val pattern = longArrayOf(0, 200, 100, 200, 100, 200)
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibrator.vibrate(effect)
            }
        }
    }
    
    companion object {
        const val ACTION_REST_FINISHED = "massey.hamhuo.timetagger.REST_FINISHED"
    }
}


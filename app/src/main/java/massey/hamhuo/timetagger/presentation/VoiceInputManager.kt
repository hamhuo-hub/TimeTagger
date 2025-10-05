package massey.hamhuo.timetagger.presentation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import java.util.*

/**
 * 语音输入管理器
 * 封装语音识别相关逻辑
 */
class VoiceInputManager(private val context: Context) {
    
    /**
     * 启动语音输入
     */
    fun startVoiceInput(
        launcher: ManagedActivityResultLauncher<Intent, ActivityResult>,
        isEdit: Boolean = false
    ) {
        val intent = createVoiceIntent(isEdit)
        selectPreferredVoiceEngine(intent)
        launcher.launch(intent)
    }
    
    /**
     * 创建语音识别Intent
     */
    private fun createVoiceIntent(isEdit: Boolean): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (isEdit) "修改任务名称" else "说出任务名称"
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            flags = Intent.FLAG_ACTIVITY_NO_HISTORY
        }
    }
    
    /**
     * 选择首选语音引擎
     */
    private fun selectPreferredVoiceEngine(intent: Intent) {
        val packageManager = context.packageManager
        val activities = packageManager.queryIntentActivities(intent, 0)
        
        val preferredPackages = listOf(
            "com.samsung.android.bixby.agent",
            "com.samsung.android.svoice",
            "com.google.android.googlequicksearchbox",
            "com.google.android.voicesearch"
        )
        
        for (pkg in preferredPackages) {
            val found = activities.find { it.activityInfo.packageName == pkg }
            if (found != null) {
                intent.component = ComponentName(
                    found.activityInfo.packageName,
                    found.activityInfo.name
                )
                break
            }
        }
    }
    
    /**
     * 从语音识别结果中提取文本
     */
    fun extractSpokenText(result: ActivityResult): String? {
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            return null
        }
        return result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
    }
}


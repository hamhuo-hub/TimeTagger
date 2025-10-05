package massey.hamhuo.timetagger.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import massey.hamhuo.timetagger.util.DateFormatter

/**
 * 时间显示辅助函数
 * 每分钟更新一次
 */
@Composable
fun rememberTimeMinuteTicker(): State<String> {
    val state = remember { mutableStateOf(DateFormatter.getCurrentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = DateFormatter.getCurrentTime()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    return state
}


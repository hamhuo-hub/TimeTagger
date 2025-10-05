package massey.hamhuo.timetagger.util

import androidx.compose.ui.graphics.Color

/**
 * 优先级配置
 */
data class PriorityConfig(
    val label: String,
    val color: Color,
    val description: String
)

/**
 * 优先级配置管理
 */
object PriorityConfigs {
    
    private val configs = mapOf(
        0 to PriorityConfig("P0", Color(0xFFEF5350), "重要且紧急"),
        1 to PriorityConfig("P1", Color(0xFF42A5F5), "重要不紧急"),
        2 to PriorityConfig("P2", Color(0xFFFFCA28), "紧急不重要"),
        3 to PriorityConfig("P3", Color(0xFF78909C), "不重要不紧急")
    )
    
    /**
     * 获取优先级配置
     */
    fun get(priority: Int): PriorityConfig? {
        return configs[priority]
    }
    
    /**
     * 获取所有配置
     */
    fun getAll(): Map<Int, PriorityConfig> {
        return configs
    }
}


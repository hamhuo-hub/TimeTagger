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
        0 to PriorityConfig("P0", Color(0xFFEF5350), "突发"),
        1 to PriorityConfig("P1", Color(0xFF42A5F5), "核心"),
        2 to PriorityConfig("P2", Color(0xFFFFCA28), "短期")
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


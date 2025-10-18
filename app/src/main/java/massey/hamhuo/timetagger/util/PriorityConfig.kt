package massey.hamhuo.timetagger.util

import androidx.compose.ui.graphics.Color

/**
 * Priority Config
 */
data class PriorityConfig(
    val label: String,
    val color: Color,
    val description: String
)

/**
 * Priority Configs Manager
 */
object PriorityConfigs {
    
    private val configs = mapOf(
        1 to PriorityConfig("P1", Color(0xFF42A5F5), "Core"),
        2 to PriorityConfig("P2", Color(0xFFFFCA28), "Urgent")
    )
    
    // Get config
    fun get(priority: Int): PriorityConfig? {
        return configs[priority]
    }
}


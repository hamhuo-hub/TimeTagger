package massey.hamhuo.timetagger.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import massey.hamhuo.timetagger.data.model.TimeRecord
import massey.hamhuo.timetagger.util.DateFormatter
import massey.hamhuo.timetagger.util.PriorityConfigs

/**
 * 历史记录屏幕
 */
@Composable
fun HistoryScreen(
    records: List<TimeRecord>,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(records) { record ->
            val startStr = DateFormatter.formatTime(record.start)
            val endStr = DateFormatter.formatTime(record.end)
            val durStr = DateFormatter.formatDuration(record.end - record.start)
            val config = PriorityConfigs.get(record.priority)
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                Text(
                    text = "$startStr - $endStr",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (config != null) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(config.color)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = "$durStr${if (record.tag.isNotEmpty()) "  ${record.tag}" else ""}",
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}


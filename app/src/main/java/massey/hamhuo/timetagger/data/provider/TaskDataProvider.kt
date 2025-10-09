package massey.hamhuo.timetagger.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import massey.hamhuo.timetagger.data.storage.CrossProcessDataReader

/**
 * ContentProvider 数据提供者
 * 为独立表盘应用提供当前任务数据
 */
class TaskDataProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "massey.hamhuo.timetagger.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/current_task")
        
        const val COLUMN_PRIORITY = "priority"
        const val COLUMN_TAG = "tag"
        const val COLUMN_REST_TIME = "rest_time"
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val context = context ?: return null
        
        // 读取当前任务数据
        val dataReader = CrossProcessDataReader(context)
        val currentTask = dataReader.getCurrentTask()
        
        // 构建游标返回数据
        val columns = arrayOf(COLUMN_PRIORITY, COLUMN_TAG, COLUMN_REST_TIME)
        val cursor = MatrixCursor(columns)
        
        cursor.addRow(arrayOf(
            currentTask.priority,
            currentTask.tag,
            currentTask.restTime
        ))
        
        return cursor
    }

    override fun getType(uri: Uri): String {
        return "vnd.android.cursor.item/vnd.$AUTHORITY.current_task"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

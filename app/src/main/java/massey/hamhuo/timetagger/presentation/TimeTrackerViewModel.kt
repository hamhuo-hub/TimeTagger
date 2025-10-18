package massey.hamhuo.timetagger.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.StateFlow
import massey.hamhuo.timetagger.data.model.CurrentTask
import massey.hamhuo.timetagger.data.model.PendingTask
import massey.hamhuo.timetagger.data.model.SuggestedTask
import massey.hamhuo.timetagger.data.model.TimeRecord
import massey.hamhuo.timetagger.service.TimeTrackerService

/**
 * Time Tracker ViewModel
 */
class TimeTrackerViewModel(
    private val service: TimeTrackerService
) : ViewModel() {
    
    // Rest state
    val isResting: StateFlow<Boolean> = service.isResting
    val restTimeLeft: StateFlow<Long> = service.restTimeLeft
    
    // Add task
    fun addTask(priority: Int, label: String) {
        service.addTask(priority, label)
    }
    
    // Add pending task
    fun addPendingTask(priority: Int, label: String) {
        service.addPendingTask(priority, label)
    }
    
    // Complete task
    fun completeTask() {
        service.completeTask()
    }
    
    // Update task tag
    fun updateCurrentTaskTag(label: String) {
        service.updateCurrentTaskTag(label)
    }
    
    // Get current task
    fun getCurrentTask(): CurrentTask {
        return service.getCurrentTask()
    }
    
    // Get pending tasks
    fun getPendingTasks(): List<PendingTask> {
        return service.getPendingTasks()
    }
    
    // Get today records
    fun getTodayRecords(): List<TimeRecord> {
        return service.getTodayRecords()
    }
    
    // Get suggested task
    fun getSuggestedTask(): SuggestedTask {
        return service.getSuggestedTask()
    }
    
    // Accept suggested task
    fun acceptSuggestedTask() {
        service.acceptSuggestedTask()
    }
    
    // Start pending task
    fun startPendingTask(task: PendingTask) {
        service.startPendingTask(task)
    }
    
    // Start rest
    fun startTaskRest() {
        service.startTaskRest()
    }
    
    // Stop rest
    fun stopTaskRest() {
        service.stopTaskRest()
    }
}

/**
 * ViewModel Factory
 */
class TimeTrackerViewModelFactory(
    private val service: TimeTrackerService
) : ViewModelProvider.Factory {
    
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimeTrackerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TimeTrackerViewModel(service) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


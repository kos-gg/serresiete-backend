package com.kos.tasks.runnables

import com.kos.tasks.TaskType
import com.kos.tasks.TasksService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.*

data class ScheduledTaskRunnable(
    val tasksService: TasksService,
    val taskType: TaskType,
    val coroutineScope: CoroutineScope
) : Runnable {
    override fun run() {
        coroutineScope.launch { tasksService.runTask(taskType, UUID.randomUUID().toString(), null) }
    }
}

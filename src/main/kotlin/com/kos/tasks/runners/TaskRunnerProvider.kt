package com.kos.tasks.runners

import com.kos.tasks.TaskType

class TaskRunnerProvider(private val runners: List<TaskRunner>) {
    fun taskRunnerFor(type: TaskType): TaskRunner? = runners.find { it.type == type }
}

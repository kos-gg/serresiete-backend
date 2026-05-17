package com.kos.tasks.runners

import com.kos.tasks.TaskType

class TaskRunnerProvider(private val runners: List<TaskRunner>) {
    fun runnerFor(type: TaskType): TaskRunner? = runners.find { it.type == type }
}

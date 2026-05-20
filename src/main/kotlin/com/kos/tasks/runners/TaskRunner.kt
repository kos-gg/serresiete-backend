package com.kos.tasks.runners

import com.kos.tasks.TaskType

sealed interface TaskRunner {
    val type: TaskType
    suspend fun run(id: String, arguments: Map<String, String>?)
}

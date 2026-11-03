package com.kos.tasks.runners

import arrow.core.Either
import com.kos.tasks.TaskCompleted
import com.kos.tasks.TaskFailed
import com.kos.tasks.TaskType

interface TaskRunner {
    val type: TaskType
    suspend fun run(arguments: Map<String, String>?): Either<TaskFailed, TaskCompleted>
}

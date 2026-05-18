package com.kos.tasks.repository

import com.kos.common.InMemoryRepository
import com.kos.common.fold
import com.kos.tasks.Task
import com.kos.tasks.TaskType
import java.time.OffsetDateTime

class TasksInMemoryRepository : TasksRepository, InMemoryRepository {
    private val tasks: MutableList<Task> = mutableListOf()

    override suspend fun insertTask(task: Task) {
        tasks.add(task)
    }

    override suspend fun updateTask(task: Task) {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index != -1) tasks[index] = task
    }

    override suspend fun getTasks(taskType: TaskType?): List<Task> {
        val allTasks = tasks.toList()

        return taskType.fold(
            { allTasks },
            { providedTaskType -> allTasks.filter { it.type == providedTaskType } }
        )
    }

    override suspend fun getTask(id: String): Task? {
        return tasks.find { it.id == id }
    }

    override suspend fun deleteOldTasks(olderThanDays: Long): Int {
        val daysAgo = OffsetDateTime.now().minusDays(olderThanDays)
        val deletedTasks = tasks.count { it.inserted < daysAgo }

        tasks.removeAll { it.inserted < daysAgo }

        return deletedTasks
    }

    override suspend fun getLastExecution(taskType: TaskType): Task? =
        tasks.toList().sortedByDescending { it.inserted }.firstOrNull { it.type == taskType }

    override suspend fun state(): List<Task> = tasks

    override suspend fun withState(initialState: List<Task>): TasksRepository {
        tasks.addAll(initialState)
        return this
    }

    override fun clear() {
        tasks.clear()
    }
}
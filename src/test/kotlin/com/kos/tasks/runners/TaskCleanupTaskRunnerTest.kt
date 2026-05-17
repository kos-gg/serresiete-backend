package com.kos.tasks.runners

import com.kos.tasks.Status
import com.kos.tasks.TaskType
import com.kos.tasks.TasksTestHelper.task
import com.kos.tasks.repository.TasksInMemoryRepository
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskCleanupTaskRunnerTest {

    private val tasksRepo = TasksInMemoryRepository()
    private val runner = TaskCleanupTaskRunner(tasksRepo)

    @Test
    fun `old tasks are deleted and cleanup task is recorded as successful`() = runBlocking {
        val now = OffsetDateTime.now()
        val recent = task(now)
        tasksRepo.withState(listOf(recent, task(now.minusDays(8))))

        val id = UUID.randomUUID().toString()

        runner.run(id, null)

        val inserted = tasksRepo.state().last()
        assertEquals(listOf(recent, inserted), tasksRepo.state())
        assertEquals(id, inserted.id)
        assertEquals(TaskType.TASK_CLEANUP_TASK, inserted.type)
        assertEquals(Status.SUCCESSFUL, inserted.taskStatus.status)
    }
}

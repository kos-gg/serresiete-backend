package com.kos.tasks.runners

import com.kos.tasks.TaskCompleted
import arrow.core.Either
import com.kos.tasks.TasksTestHelper.task
import com.kos.tasks.repository.TasksInMemoryRepository
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
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

        val result = runner.run(null)

        assertEquals(listOf(recent), tasksRepo.state())
        assertEquals(Either.Right(TaskCompleted()), result)
    }
}

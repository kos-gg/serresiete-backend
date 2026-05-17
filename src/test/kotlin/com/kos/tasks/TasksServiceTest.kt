package com.kos.tasks

import com.kos.tasks.TasksTestHelper.task
import com.kos.tasks.repository.TasksInMemoryRepository
import com.kos.tasks.runners.TaskRunnerProvider
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TasksServiceTest {

    private val tasksRepo = TasksInMemoryRepository()
    private val tasksService = TasksService(tasksRepo, TaskRunnerProvider(emptyList()))

    @Test
    fun `getTasks returns all tasks`() = runBlocking {
        val now = OffsetDateTime.now()
        val expected = task(now)
        tasksRepo.withState(listOf(expected))

        assertEquals(listOf(expected), tasksService.getTasks(null))
    }

    @Test
    fun `getTask returns task by id`() = runBlocking {
        val now = OffsetDateTime.now()
        val expected = task(now).copy(id = "known-id")
        tasksRepo.withState(listOf(expected))

        assertEquals(expected, tasksService.getTask("known-id"))
    }
}

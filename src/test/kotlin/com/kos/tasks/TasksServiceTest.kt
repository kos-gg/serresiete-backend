package com.kos.tasks

import com.kos.tasks.TasksTestHelper.task
import com.kos.tasks.repository.TasksInMemoryRepository
import com.kos.tasks.runners.TaskRunner
import com.kos.tasks.runners.TaskRunnerProvider
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `runTask inserts a pending task and delegates to the runner`() = runBlocking {
        val runner = mockk<TaskRunner>(relaxed = true)
        val service = TasksService(tasksRepo, TaskRunnerProvider(listOf(runner)))
        every { runner.type } returns TaskType.CACHE_WOW_DATA_TASK

        service.runTask(TaskType.CACHE_WOW_DATA_TASK, "task-id", null)

        val inserted = tasksRepo.getTask("task-id")
        assertEquals("task-id", inserted?.id)
        assertEquals(TaskType.CACHE_WOW_DATA_TASK, inserted?.type)
        assertEquals(Status.PENDING, inserted?.taskStatus?.status)
        assertNull(inserted?.taskStatus?.message)
        coVerify(exactly = 1) { runner.run("task-id", null) }
    }

    @Test
    fun `runTask does nothing when no runner is found for the task type`() = runBlocking {
        tasksService.runTask(TaskType.CACHE_WOW_DATA_TASK, "task-id", null)

        assertTrue(tasksRepo.getTasks(null).isEmpty())
    }
}

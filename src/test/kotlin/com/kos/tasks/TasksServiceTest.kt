package com.kos.tasks

import com.kos.tasks.TasksTestHelper.task
import com.kos.tasks.repository.TasksInMemoryRepository
import com.kos.tasks.runners.TaskRunner
import com.kos.tasks.runners.TaskRunnerProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `runTask inserts a pending task and returns its id`() = runBlocking {
        val runner = mockk<TaskRunner>(relaxed = true)
        val service = TasksService(tasksRepo, TaskRunnerProvider(listOf(runner)))
        every { runner.type } returns TaskType.CACHE_WOW_DATA_TASK

        val taskId = service.runTask(TaskType.CACHE_WOW_DATA_TASK, null)

        val inserted = tasksRepo.getTask(taskId)
        assertEquals(taskId, inserted?.id)
        assertEquals(TaskType.CACHE_WOW_DATA_TASK, inserted?.type)
        assertEquals(Status.PENDING, inserted?.taskStatus?.status)
        assertNull(inserted?.taskStatus?.message)
    }

    @Test
    fun `runTask throws and inserts nothing when no runner is registered for the task type`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            tasksService.runTask(TaskType.CACHE_WOW_DATA_TASK, null)
        }
        assertTrue(tasksRepo.getTasks(null).isEmpty())
    }
}

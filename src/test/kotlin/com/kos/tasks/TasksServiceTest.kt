package com.kos.tasks

import arrow.core.Either
import com.kos.tasks.TasksTestHelper.task
import com.kos.tasks.repository.TasksInMemoryRepository
import com.kos.tasks.runners.TaskRunner
import com.kos.tasks.runners.TaskRunnerProvider
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        val runner = mockk<TaskRunner>()
        val service = TasksService(tasksRepo, TaskRunnerProvider(listOf(runner)))
        every { runner.type } returns TaskType.CACHE_WOW_DATA_TASK
        coEvery { runner.run(any()) } coAnswers { awaitCancellation() }

        val taskId = service.runTask(TaskType.CACHE_WOW_DATA_TASK, null)

        val inserted = tasksRepo.getTask(taskId)
        assertEquals(taskId, inserted?.id)
        assertEquals(TaskType.CACHE_WOW_DATA_TASK, inserted?.type)
        assertEquals(Status.PENDING, inserted?.taskStatus?.status)
    }

    @Test
    fun `a task whose runner throws is marked as failed and keeps the stack trace internally`() = runBlocking {
        val runner = mockk<TaskRunner>()
        val service = TasksService(tasksRepo, TaskRunnerProvider(listOf(runner)))
        val arguments = mapOf("viewId" to "1")
        every { runner.type } returns TaskType.CACHE_GAME_VIEW_DATA_TASK
        coEvery { runner.run(arguments) } throws IllegalStateException("boom")

        val taskId = service.runTask(TaskType.CACHE_GAME_VIEW_DATA_TASK, arguments)

        val failed = awaitStatus(taskId, Status.ERROR)
        assertEquals(TaskStatus(Status.ERROR), failed.taskStatus)
        assertEquals(arguments, failed.error?.arguments)
        assertTrue(failed.error!!.detail.startsWith("java.lang.IllegalStateException: boom"))
        assertTrue(failed.error!!.detail.contains("\tat "))
    }

    @Test
    fun `a task whose runner completes is marked as successful with the runner's retryAfter`() = runBlocking {
        val runner = mockk<TaskRunner>()
        val service = TasksService(tasksRepo, TaskRunnerProvider(listOf(runner)))
        val retryAfter = OffsetDateTime.now().plusMinutes(5)
        every { runner.type } returns TaskType.CACHE_GAME_VIEW_DATA_TASK
        coEvery { runner.run(any()) } returns Either.Right(TaskCompleted(retryAfter))

        val taskId = service.runTask(TaskType.CACHE_GAME_VIEW_DATA_TASK, mapOf("viewId" to "1"))

        val completed = awaitStatus(taskId, Status.SUCCESSFUL)
        assertEquals(TaskStatus(Status.SUCCESSFUL, retryAfter), completed.taskStatus)
        assertEquals(null, completed.error)
    }

    @Test
    fun `a task whose runner fails is marked as failed with the runner's detail, arguments and retryAfter`() = runBlocking {
        val runner = mockk<TaskRunner>()
        val service = TasksService(tasksRepo, TaskRunnerProvider(listOf(runner)))
        val arguments = mapOf("viewId" to "1")
        val retryAfter = OffsetDateTime.now().plusMinutes(5)
        every { runner.type } returns TaskType.CACHE_GAME_VIEW_DATA_TASK
        coEvery { runner.run(arguments) } returns Either.Left(TaskFailed("view 1 was synced recently", retryAfter))

        val taskId = service.runTask(TaskType.CACHE_GAME_VIEW_DATA_TASK, arguments)

        val failed = awaitStatus(taskId, Status.ERROR)
        assertEquals(TaskStatus(Status.ERROR, retryAfter), failed.taskStatus)
        assertEquals(TaskError(arguments, "view 1 was synced recently"), failed.error)
    }

    @Test
    fun `a cancelled task is not marked as failed`() = runBlocking {
        val runner = mockk<TaskRunner>()
        val service = TasksService(tasksRepo, TaskRunnerProvider(listOf(runner)))
        val started = CompletableDeferred<Unit>()
        every { runner.type } returns TaskType.CACHE_WOW_DATA_TASK
        coEvery { runner.run(any()) } coAnswers {
            started.complete(Unit)
            throw CancellationException("shutting down")
        }

        val taskId = service.runTask(TaskType.CACHE_WOW_DATA_TASK, null)
        started.await()
        delay(100)

        assertEquals(TaskStatus(Status.PENDING), tasksRepo.getTask(taskId)?.taskStatus)
    }

    private suspend fun awaitStatus(id: String, status: Status): Task =
        withTimeout(2000) { pollStatus(id, status) }

    private tailrec suspend fun pollStatus(id: String, status: Status): Task {
        val task = tasksRepo.getTask(id)
        if (task?.taskStatus?.status == status) return task
        delay(10)
        return pollStatus(id, status)
    }

    @Test
    fun `runTask throws and inserts nothing when no runner is registered for the task type`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            tasksService.runTask(TaskType.CACHE_WOW_DATA_TASK, null)
        }
        assertTrue(tasksRepo.getTasks(null).isEmpty())
    }
}

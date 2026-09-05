package com.kos.tasks

import com.kos.activities.Activities
import com.kos.tasks.TasksTestHelper.task
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TasksControllerTest {

    private val taskService = mock(TasksService::class.java)

    private val tasksController = TasksController(taskService)

    @Test
    fun `i can get tasks`() {
        runBlocking {
            val now = OffsetDateTime.now()
            val task = task(now)

            whenever(taskService.getTasks(TaskType.CACHE_GAME_VIEW_DATA_TASK))
                .thenReturn(listOf(task))

            assertEquals(
                listOf(task),
                tasksController.getTasks("owner", setOf(Activities.getTasks), TaskType.CACHE_GAME_VIEW_DATA_TASK)
                    .getOrNull()
            )
        }
    }

    @Test
    fun `i can get task by id`() {
        runBlocking {
            val now = OffsetDateTime.now()
            val knownId = "1"
            val task = task(now).copy(id = knownId)

            whenever(taskService.getTask(knownId))
                .thenReturn(task)

            assertEquals(task, tasksController.getTask("owner", knownId, setOf(Activities.getTask)).getOrNull())
        }
    }

    @Test
    fun `i can run a task`() {
        runBlocking {
            whenever(taskService.runTask(eq(TaskType.CACHE_GAME_VIEW_DATA_TASK), eq(null)))
                .thenReturn("task-id")

            val result =
                tasksController.runTask("owner", TaskRequest(TaskType.CACHE_GAME_VIEW_DATA_TASK), setOf(Activities.runTask))

            assertEquals("task-id", result.getOrNull())
            verify(taskService).runTask(
                eq(TaskType.CACHE_GAME_VIEW_DATA_TASK),
                eq(null)
            )
        }
    }
}

package com.kos.tasks.runners

import com.kos.datacache.DataCacheService
import com.kos.datacache.TestHelper.lolDataCache
import com.kos.datacache.TestHelper.wowDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksInMemoryRepository
import com.kos.views.Game
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheClearTaskRunnerTest {

    private val tasksRepo = TasksInMemoryRepository()
    private val dataCacheRepo = DataCacheInMemoryRepository()
    private val dataCacheService = DataCacheService(dataCacheRepo, EntitiesInMemoryRepository(), EventStoreInMemory())
    private val runner = CacheClearTaskRunner(tasksRepo, dataCacheService)

    @Test
    fun `all cache is cleared when no game argument is provided`() = runBlocking {
        dataCacheRepo.withState(listOf(wowDataCache, lolDataCache))
        val id = UUID.randomUUID().toString()

        tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        runner.run(id, null)

        assertEquals(emptyList(), dataCacheRepo.state())
        val task = tasksRepo.state().first()
        assertEquals(id, task.id)
        assertEquals(TaskType.CACHE_CLEAR_TASK, task.type)
        assertEquals(Status.SUCCESSFUL, task.taskStatus.status)
    }

    @Test
    fun `only lol cache is cleared when game argument is lol`() = runBlocking {
        dataCacheRepo.withState(listOf(wowDataCache, lolDataCache))
        val id = UUID.randomUUID().toString()
        tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        runner.run(id, mapOf("game" to Game.LOL.toString()))

        assertEquals(listOf(wowDataCache), dataCacheRepo.state())
        val task = tasksRepo.state().first()
        assertEquals(id, task.id)
        assertEquals(TaskType.CACHE_CLEAR_TASK, task.type)
        assertEquals(Status.SUCCESSFUL, task.taskStatus.status)
    }
}

package com.kos.tasks.runners

import com.kos.tasks.TaskCompleted
import arrow.core.Either
import com.kos.datacache.DataCacheService
import com.kos.datacache.TestHelper.lolDataCache
import com.kos.datacache.TestHelper.wowDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.views.Game
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheClearTaskRunnerTest {

    private val dataCacheRepo = DataCacheInMemoryRepository()
    private val dataCacheService = DataCacheService(dataCacheRepo, EntitiesInMemoryRepository(), EventStoreInMemory())
    private val runner = CacheClearTaskRunner(dataCacheService)

    @Test
    fun `all cache is cleared when no game argument is provided`() = runBlocking {
        dataCacheRepo.withState(listOf(wowDataCache, lolDataCache))

        val result = runner.run(null)

        assertEquals(emptyList(), dataCacheRepo.state())
        assertEquals(Either.Right(TaskCompleted()), result)
    }

    @Test
    fun `only lol cache is cleared when game argument is lol`() = runBlocking {
        dataCacheRepo.withState(listOf(wowDataCache, lolDataCache))

        val result = runner.run(mapOf("game" to Game.LOL.toString()))

        assertEquals(listOf(wowDataCache), dataCacheRepo.state())
        assertEquals(Either.Right(TaskCompleted()), result)
    }
}

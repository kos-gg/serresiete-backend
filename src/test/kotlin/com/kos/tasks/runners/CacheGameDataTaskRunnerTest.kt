package com.kos.tasks.runners

import com.kos.tasks.TaskFailed
import com.kos.tasks.TaskCompleted
import arrow.core.Either
import com.kos.datacache.domain.QueueType
import com.kos.clients.riot.RiotClient
import com.kos.clients.TimeoutError
import com.kos.datacache.RiotMockHelper
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.ThirdPartyCallFailed
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.sync.EntitySynchronizer
import com.kos.entities.sync.EntitySynchronizerProvider
import com.kos.entities.sync.SyncBudget
import com.kos.entities.sync.SyncEntitySelector
import com.kos.entities.sync.rules.StalenessSyncRule
import com.kos.sources.lol.LolEntitySynchronizer
import com.kos.tasks.TaskType
import com.kos.views.Game
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheGameDataTaskRunnerTest {

    private val riotClient = mock(RiotClient::class.java)
    private val dataCacheRepo = DataCacheInMemoryRepository()
    private val entitiesRepository = EntitiesInMemoryRepository()
    private fun syncEntitySelector(entitiesRepository: EntitiesRepository) = SyncEntitySelector(
        StalenessSyncRule(
            entitiesRepository,
            30,
            SyncBudget(mapOf(Game.LOL to Int.MAX_VALUE, Game.WOW to Int.MAX_VALUE, Game.WOW_HC to Int.MAX_VALUE))
        )
    )

    @Test
    fun `entities are synced and task is recorded as successful`() = runBlocking {
        entitiesRepository.withState(EntitiesState(listOf(), listOf(), listOf(basicLolEntity)))
        val runner = CacheGameDataTaskRunner(
            Game.LOL,
            TaskType.CACHE_LOL_DATA_TASK,
            syncEntitySelector(entitiesRepository),
            EntitySynchronizerProvider(
                wowSynchronizer = mock(),
                wowHardcoreSynchronizer = mock(),
                lolSynchronizer = LolEntitySynchronizer(dataCacheRepo, riotClient)
            )
        )

        `when`(riotClient.getLeagueEntriesByPUUID(basicLolEntity.puuid)).thenReturn(RiotMockHelper.leagueEntries)
        `when`(
            riotClient.getMatchesByPuuid(
                basicLolEntity.puuid,
                QueueType.SOLO_Q
            )
        ).thenReturn(RiotMockHelper.matches)
        `when`(
            riotClient.getMatchesByPuuid(
                basicLolEntity.puuid,
                QueueType.FLEX_Q
            )
        ).thenReturn(RiotMockHelper.matches)
        `when`(riotClient.getMatchById(RiotMockHelper.matchId)).thenReturn(Either.Right(RiotMockHelper.match))

        val result = runner.run(null)

        assertEquals(1, dataCacheRepo.state().size)
        assertEquals(Either.Right(TaskCompleted()), result)
    }

    @Test
    fun `task is recorded as error and the synchronizer's errors are stored when synchronization fails`() =
        runBlocking {
            entitiesRepository.withState(EntitiesState(listOf(), listOf(), listOf(basicLolEntity)))
            val failingSynchronizer = mock(EntitySynchronizer::class.java)
            `when`(failingSynchronizer.synchronize(listOf(basicLolEntity)))
                .thenReturn(listOf(ThirdPartyCallFailed(1, TimeoutError("boom"))))
            val runner = CacheGameDataTaskRunner(
                Game.LOL,
                TaskType.CACHE_LOL_DATA_TASK,
                syncEntitySelector(entitiesRepository),
                EntitySynchronizerProvider(
                    wowSynchronizer = mock(),
                    wowHardcoreSynchronizer = mock(),
                    lolSynchronizer = failingSynchronizer
                )
            )

            val result = runner.run(null)

            assertEquals(Either.Left(TaskFailed("Entity 1: TimeoutError(message=boom)")), result)
        }
}

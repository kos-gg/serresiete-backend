package com.kos.datacache

import com.kos.clients.domain.RaiderIoData
import com.kos.clients.domain.RiotData
import com.kos.datacache.TestHelper.lolDataCache
import com.kos.datacache.TestHelper.wowDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.views.ViewEntity
import com.kos.views.ViewsTestHelper.basicSimpleWowHardcoreView
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataCacheServiceTest {

    //TODO - this class will need to be more exhaustive

    @Test
    fun `i can get wow data`() {
        runBlocking {
            val repo = DataCacheInMemoryRepository().withState(
                listOf(
                    wowDataCache,
                    wowDataCache.copy(entityId = 2, data = wowDataCache.data.replace(""""id": 1""", """"id": 2""")),
                    wowDataCache.copy(entityId = 3, data = wowDataCache.data.replace(""""id": 1""", """"id": 3"""))
                )
            )

            val data = createService(repo).getData(listOf(1, 3), oldFirst = true)
            assertTrue(data.isRight { it.size == 2 })
            assertEquals(listOf<Long>(1, 3), data.map {
                it.map { d ->
                    d as RaiderIoData
                    d.id
                }
            }.getOrNull())
        }
    }

    @Test
    fun `i can get lol data`() {
        runBlocking {
            val repo = DataCacheInMemoryRepository().withState(
                listOf(lolDataCache)
            )
            val data = createService(repo).getData(listOf(2), oldFirst = true)

            assertTrue(data.isRight { it.size == 1 })
            assertEquals(listOf(basicLolEntity.name), data.map {
                it.map { d ->
                    d as RiotData
                    d.summonerName
                }
            }.getOrNull())
        }
    }

    @Test
    fun `i can verify that getting data returns the oldest cached data record stored`() {
        runBlocking {
            val repo = DataCacheInMemoryRepository().withState(
                listOf(
                    wowDataCache.copy(inserted = wowDataCache.inserted.minusHours(10)),
                    wowDataCache.copy(data = wowDataCache.data.replace(""""score": 0.0""", """"score": 1.0""")),
                )
            )
            val data = createService(repo).getData(listOf(1), oldFirst = true)
            assertTrue(data.isRight { it.size == 1 })
            assertEquals(listOf(0.0), data.map {
                it.map { d ->
                    d as RaiderIoData
                    d.score
                }
            }.getOrNull())
        }
    }

    @Test
    fun `it must not return no data and sync operation when getOrSync entity was not in the system`() {

    }

    private suspend fun createService(dataCacheRepository: DataCacheInMemoryRepository): DataCacheService {
        val viewsRepository = ViewsInMemoryRepository()
            .withState(
                ViewsState(
                    listOf(basicSimpleWowHardcoreView.copy(entitiesIds = listOf(1))),
                    basicSimpleWowHardcoreView.entitiesIds.map {
                        ViewEntity(
                            it,
                            basicSimpleWowHardcoreView.id,
                            "alias"
                        )
                    }
                )
            )
        val entitiesRepository = EntitiesInMemoryRepository(dataCacheRepository, viewsRepository)
            .withState(
                EntitiesState(
                    listOf(),
                    listOf(basicWowHardcoreEntity, basicWowHardcoreEntity.copy(id = 2, blizzardId = 123)),
                    listOf()
                )
            )

        val eventsStore = EventStoreInMemory()


        return DataCacheService(
            dataCacheRepository,
            entitiesRepository,
            eventsStore
        )
    }

}
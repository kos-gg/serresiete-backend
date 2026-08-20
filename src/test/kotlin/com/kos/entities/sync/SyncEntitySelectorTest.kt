package com.kos.entities.sync

import com.kos.datacache.TestHelper.wowDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowEntity2
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.sync.rules.StalenessSyncRule
import com.kos.views.Game
import com.kos.views.ViewEntity
import com.kos.views.ViewsTestHelper.basicSimpleWowHardcoreView
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncEntitySelectorTest {

    @Test
    fun `staleness rule excludes entities with fresh cache`() {
        runBlocking {
            val freshCache = wowDataCache.copy(entityId = basicWowEntity.id)
            val staleCache =
                wowDataCache.copy(entityId = basicWowEntity2.id, inserted = wowDataCache.inserted.minusHours(2))

            val selector = createSelector(listOf(freshCache, staleCache))
            val result = selector.select(Game.WOW)

            assertEquals(listOf(basicWowEntity2), result)
        }
    }

    @Test
    fun `staleness rule includes entities with no cache`() {
        runBlocking {
            val selector = createSelector()
            val result = selector.select(Game.WOW)

            assertEquals(listOf(basicWowEntity, basicWowEntity2), result)
        }
    }

    private suspend fun createSelector(
        caches: List<com.kos.datacache.DataCache> = emptyList()
    ): SyncEntitySelector {
        val dataCacheRepository = DataCacheInMemoryRepository().withState(caches)
        val viewsRepository = ViewsInMemoryRepository().withState(
            ViewsState(
                listOf(basicSimpleWowHardcoreView),
                basicSimpleWowHardcoreView.entitiesIds.map { ViewEntity(it, basicSimpleWowHardcoreView.id, "alias") }
            )
        )
        val entitiesRepository = EntitiesInMemoryRepository(dataCacheRepository, viewsRepository)
            .withState(EntitiesState(listOf(basicWowEntity, basicWowEntity2), listOf(basicWowHardcoreEntity), listOf()))
        val stalenessRule = StalenessSyncRule(
            entitiesRepository,
            30,
            SyncBudget(mapOf(Game.LOL to Int.MAX_VALUE, Game.WOW to Int.MAX_VALUE, Game.WOW_HC to Int.MAX_VALUE))
        )

        return SyncEntitySelector(stalenessRule)
    }
}

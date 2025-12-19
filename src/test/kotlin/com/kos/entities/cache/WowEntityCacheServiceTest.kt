package com.kos.entities.cache

import com.kos.clients.raiderio.RaiderIoClient
import com.kos.common.RetryConfig
import com.kos.datacache.RaiderIoMockHelper
import com.kos.datacache.TestHelper.wowDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test

class WowEntityCacheServiceTest {

    //TODO - this could ne more exhaustive

    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val retryConfig = RetryConfig(1, 1)

    @Test
    fun `i can cache wow data`() {
        runBlocking {
            `when`(raiderIoClient.get(basicWowEntity)).thenReturn(RaiderIoMockHelper.get(basicWowEntity))
            `when`(raiderIoClient.get(basicWowEntity.copy(id = 2))).thenReturn(
                RaiderIoMockHelper.get(basicWowEntity.copy(id = 2))
            )
            `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())

            val repo = DataCacheInMemoryRepository().withState(listOf(wowDataCache))
            assertEquals(listOf(wowDataCache), repo.state())

            WowEntityCacheService(repo, raiderIoClient, retryConfig)
                .cache(listOf(basicWowEntity, basicWowEntity.copy(id = 2)))
            assertEquals(3, repo.state().size)
        }
    }
}
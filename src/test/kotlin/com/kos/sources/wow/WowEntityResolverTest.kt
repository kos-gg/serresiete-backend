package com.kos.sources.wow

import com.kos.clients.raiderio.RaiderIoClient
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.entities.EntitiesTestHelper.basicWowRequest2
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class WowEntityResolverTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)

    @Test
    fun `resolves a new character that exists in raiderio`() {
        runBlocking {
            `when`(raiderIoClient.exists(basicWowRequest)).thenReturn(true)

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(basicWowRequest to null), res.entities)
                    assertEquals(listOf(), res.existing)
                }
        }
    }

    @Test
    fun `does not resolve a new character that does not exist in raiderio`() {
        runBlocking {
            `when`(raiderIoClient.exists(basicWowRequest)).thenReturn(false)

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res -> assertEquals(listOf(), res.entities) }
        }
    }

    @Test
    fun `a character already in the repository is returned as existing without calling raiderio`() {
        runBlocking {
            val repo =
                EntitiesInMemoryRepository().withState(EntitiesState(listOf(basicWowEntity), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(basicWowEntity to null), res.existing)
                    assertEquals(listOf(), res.entities)
                }

            verifyNoInteractions(raiderIoClient)
        }
    }

    @Test
    fun `resolves a batch of new characters concurrently, keeping only the ones that exist`() {
        runBlocking {
            `when`(raiderIoClient.exists(basicWowRequest)).thenReturn(true)
            `when`(raiderIoClient.exists(basicWowRequest2)).thenReturn(false)

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient)

            resolver.resolve(listOf(basicWowRequest, basicWowRequest2), null)
                .onLeft { fail() }
                .onRight { res -> assertEquals(listOf(basicWowRequest to null), res.entities) }
        }
    }
}

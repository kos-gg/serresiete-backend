package com.kos.sources.wow

import arrow.core.Either
import com.kos.clients.TimeoutError
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
            `when`(raiderIoClient.exists(basicWowRequest)).thenReturn(Either.Right(true))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(basicWowRequest to null), res.entities)
                    assertEquals(listOf(), res.existing)
                    assertEquals(listOf(), res.unchecked)
                }
        }
    }

    @Test
    fun `does not resolve a new character that does not exist in raiderio`() {
        runBlocking {
            `when`(raiderIoClient.exists(basicWowRequest)).thenReturn(Either.Right(false))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(listOf(), res.unchecked)
                }
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
            `when`(raiderIoClient.exists(basicWowRequest)).thenReturn(Either.Right(true))
            `when`(raiderIoClient.exists(basicWowRequest2)).thenReturn(Either.Right(false))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient)

            resolver.resolve(listOf(basicWowRequest, basicWowRequest2), null)
                .onLeft { fail() }
                .onRight { res -> assertEquals(listOf(basicWowRequest to null), res.entities) }
        }
    }

    @Test
    fun `resolve reports a raiderio failure as unchecked instead of lying that the character doesn't exist`() {
        runBlocking {
            val timeoutError = TimeoutError("Request timeout has expired")
            `when`(raiderIoClient.exists(basicWowRequest)).thenReturn(Either.Left(timeoutError))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(1, res.unchecked.size)
                    assertEquals(basicWowRequest, res.unchecked.single().first)
                }
        }
    }

    @Test
    fun `resolve partitions a mixed batch into resolved entities and unchecked ones`() {
        runBlocking {
            val timeoutError = TimeoutError("Request timeout has expired")
            val basicWowRequest3 = basicWowRequest2.copy(name = "thirdcharacter")

            `when`(raiderIoClient.exists(basicWowRequest)).thenReturn(Either.Right(true))
            `when`(raiderIoClient.exists(basicWowRequest2)).thenReturn(Either.Right(false))
            `when`(raiderIoClient.exists(basicWowRequest3)).thenReturn(Either.Left(timeoutError))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient)

            resolver.resolve(listOf(basicWowRequest, basicWowRequest2, basicWowRequest3), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(basicWowRequest to null), res.entities)
                    assertEquals(listOf(basicWowRequest3), res.unchecked.map { it.first })
                }
        }
    }
}

package com.kos.sources.wow

import arrow.core.Either
import com.kos.clients.HttpError
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.datacache.BlizzardMockHelper
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.entities.EntitiesTestHelper.basicWowRequest2
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class WowEntityResolverTest {
    private val blizzardClient = mock(BlizzardClient::class.java)

    @Test
    fun `resolves a new character that exists in blizzard`() {
        runBlocking {
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowEntity))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, blizzardClient)

            resolver.resolve(listOf(basicWowRequest))
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(
                        listOf(
                            WowEntityRequest(
                                basicWowRequest.name,
                                basicWowRequest.region,
                                basicWowRequest.realm,
                                basicWowEntity.id
                            ) to null
                        ),
                        res.entities
                    )
                    assertEquals(listOf(), res.existing)
                    assertEquals(listOf(), res.unchecked)
                }
        }
    }

    @Test
    fun `does not resolve a new character that does not exist in blizzard`() {
        runBlocking {
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(Either.Left(HttpError(404, null)))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, blizzardClient)

            resolver.resolve(listOf(basicWowRequest))
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(listOf(), res.unchecked)
                }
        }
    }

    @Test
    fun `a character already in the repository is returned as existing without calling blizzard`() {
        runBlocking {
            val repo =
                EntitiesInMemoryRepository().withState(EntitiesState(listOf(basicWowEntity), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, blizzardClient)

            resolver.resolve(listOf(basicWowRequest))
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(basicWowEntity to null), res.existing)
                    assertEquals(listOf(), res.entities)
                }

            verifyNoInteractions(blizzardClient)
        }
    }

    @Test
    fun `resolves a batch of new characters concurrently, keeping only the ones that exist`() {
        runBlocking {
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowEntity))
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest2.region, basicWowRequest2.realm, basicWowRequest2.name)
            ).thenReturn(Either.Left(HttpError(404, null)))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, blizzardClient)

            resolver.resolve(listOf(basicWowRequest, basicWowRequest2))
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(
                        listOf(
                            WowEntityRequest(
                                basicWowRequest.name,
                                basicWowRequest.region,
                                basicWowRequest.realm,
                                basicWowEntity.id
                            ) to null
                        ),
                        res.entities
                    )
                }
        }
    }

    @Test
    fun `resolve reports a blizzard failure as unchecked instead of lying that the character doesn't exist`() {
        runBlocking {
            val timeoutError = TimeoutError("Request timeout has expired")
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(Either.Left(timeoutError))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, blizzardClient)

            resolver.resolve(listOf(basicWowRequest))
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

            `when`(
                blizzardClient.getRetailProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowEntity))
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest2.region, basicWowRequest2.realm, basicWowRequest2.name)
            ).thenReturn(Either.Left(HttpError(404, null)))
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest3.region, basicWowRequest3.realm, basicWowRequest3.name)
            ).thenReturn(Either.Left(timeoutError))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, blizzardClient)

            resolver.resolve(listOf(basicWowRequest, basicWowRequest2, basicWowRequest3))
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(
                        listOf(
                            WowEntityRequest(
                                basicWowRequest.name,
                                basicWowRequest.region,
                                basicWowRequest.realm,
                                basicWowEntity.id
                            ) to null
                        ),
                        res.entities
                    )
                    assertEquals(listOf(basicWowRequest3), res.unchecked.map { it.first })
                }
        }
    }
}

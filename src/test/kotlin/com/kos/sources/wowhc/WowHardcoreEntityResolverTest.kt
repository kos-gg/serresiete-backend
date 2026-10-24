package com.kos.sources.wowhc

import arrow.core.Either
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.blizzard.toWowRealm
import com.kos.datacache.BlizzardMockHelper
import com.kos.datacache.BlizzardMockHelper.hardcoreRealm
import com.kos.datacache.BlizzardMockHelper.notHardcoreRealm
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.entities.domain.EntityRequest
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class WowHardcoreEntityResolverTest {
    private val blizzardClient = mock(BlizzardClient::class.java)

    @Test
    fun `resolves a new character on a hardcore realm`() {
        runBlocking {
            `when`(
                blizzardClient.getClassicProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowRequest))
            `when`(blizzardClient.getRealm(basicWowRequest.region, 5220)).thenReturn(Either.Right(hardcoreRealm.toWowRealm()))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowHardcoreEntityResolver(repo, blizzardClient)

            resolver.resolve(listOf(basicWowRequest))
                .let { res ->
                    assertEquals(1, res.entities.size)
                    assertEquals(basicWowRequest.name, res.entities.first().first.name)
                }
        }
    }

    @Test
    fun `does not resolve a character from a non hardcore realm`() {
        runBlocking {
            `when`(
                blizzardClient.getClassicProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowRequest))
            `when`(blizzardClient.getRealm(basicWowRequest.region, 5220)).thenReturn(Either.Right(notHardcoreRealm.toWowRealm()))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowHardcoreEntityResolver(repo, blizzardClient)

            resolver.resolve(listOf(basicWowRequest))
                .let { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(listOf(), res.unchecked)
                }
        }
    }

    @Test
    fun `a character that blizzard could not check is reported as unchecked`() {
        runBlocking {
            `when`(
                blizzardClient.getClassicProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(Either.Left(TimeoutError("timeout")))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowHardcoreEntityResolver(repo, blizzardClient)

            resolver.resolve(listOf(basicWowRequest))
                .let { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(listOf<EntityRequest>(basicWowRequest), res.unchecked.map { it.first })
                }
        }
    }

    @Test
    fun `a character whose realm could not be checked is reported as unchecked`() {
        runBlocking {
            `when`(
                blizzardClient.getClassicProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowRequest))
            `when`(blizzardClient.getRealm(basicWowRequest.region, 5220)).thenReturn(Either.Left(TimeoutError("timeout")))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowHardcoreEntityResolver(repo, blizzardClient)

            resolver.resolve(listOf(basicWowRequest))
                .let { res -> assertEquals(listOf<EntityRequest>(basicWowRequest), res.unchecked.map { it.first }) }
        }
    }

    @Test
    fun `a character not found in blizzard is neither resolved nor unchecked`() {
        runBlocking {
            `when`(
                blizzardClient.getClassicProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(Either.Right(null))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowHardcoreEntityResolver(repo, blizzardClient)

            resolver.resolve(listOf(basicWowRequest))
                .let { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(listOf(), res.unchecked)
                }
        }
    }

    @Test
    fun `a character already in the repository is returned as existing without calling blizzard`() {
        runBlocking {
            val repo = EntitiesInMemoryRepository()
                .withState(EntitiesState(listOf(), listOf(basicWowHardcoreEntity), listOf()))
            val resolver = WowHardcoreEntityResolver(repo, blizzardClient)

            resolver.resolve(listOf(basicWowRequest))
                .let { res -> assertEquals(listOf(basicWowHardcoreEntity to null), res.existing) }

            verifyNoInteractions(blizzardClient)
        }
    }
}

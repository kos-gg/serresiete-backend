package com.kos.sources.wowforever

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.NotFound
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.entities.EntitiesTestHelper.basicWowForeverEntity
import com.kos.entities.EntitiesTestHelper.basicWowForeverRequest
import com.kos.entities.EntitiesTestHelper.basicWowForeverRequestWithBlizzardId
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.InsertEntityRequest
import com.kos.entities.domain.WowForeverCharacter
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.sources.wowforever.WowForeverTestHelper.foreverCharacter
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import kotlin.test.Test
import kotlin.test.assertEquals

class WowForeverEntityResolverTest {
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val emptyRepo = EntitiesInMemoryRepository()

    private suspend fun givenProfile(response: Either<ClientError, WowForeverCharacter>) =
        `when`(
            blizzardClient.getForeverProfile(
                basicWowForeverRequest.region,
                basicWowForeverRequest.name,
                basicWowForeverRequest.surname
            )
        ).thenReturn(response)

    @Test
    fun `resolves a new character keeping the blizzard id`() {
        runBlocking {
            givenProfile(Either.Right(foreverCharacter))

            val resolved = WowForeverEntityResolver(emptyRepo, blizzardClient).resolve(listOf(basicWowForeverRequest))

            assertEquals(listOf<Pair<InsertEntityRequest, String?>>(basicWowForeverRequestWithBlizzardId to null), resolved.entities)
            assertEquals(listOf(), resolved.unchecked)
        }
    }

    @Test
    fun `a character not found in blizzard is neither resolved nor unchecked`() {
        runBlocking {
            givenProfile(Either.Left(NotFound))

            val resolved = WowForeverEntityResolver(emptyRepo, blizzardClient).resolve(listOf(basicWowForeverRequest))

            assertEquals(listOf(), resolved.entities)
            assertEquals(listOf(), resolved.unchecked)
        }
    }

    @Test
    fun `a character that blizzard could not check is reported as unchecked`() {
        runBlocking {
            givenProfile(Either.Left(TimeoutError("timeout")))

            val resolved = WowForeverEntityResolver(emptyRepo, blizzardClient).resolve(listOf(basicWowForeverRequest))

            assertEquals(listOf(), resolved.entities)
            assertEquals(listOf<EntityRequest>(basicWowForeverRequest), resolved.unchecked)
        }
    }

    @Test
    fun `a character already in the repository is returned as existing without calling blizzard`() {
        runBlocking {
            val repo = EntitiesInMemoryRepository()
                .withState(EntitiesState(listOf(), listOf(), listOf(), listOf(basicWowForeverEntity)))

            val resolved = WowForeverEntityResolver(repo, blizzardClient).resolve(listOf(basicWowForeverRequest))

            assertEquals(listOf(basicWowForeverEntity to null), resolved.existing)
            assertEquals(listOf(), resolved.entities)
            verifyNoInteractions(blizzardClient)
        }
    }
}

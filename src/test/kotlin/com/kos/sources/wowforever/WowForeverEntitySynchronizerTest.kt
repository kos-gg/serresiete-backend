package com.kos.sources.wowforever

import arrow.core.Either
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.datacache.domain.Data
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesTestHelper.basicWowForeverEntity
import com.kos.entities.ThirdPartyCallFailed
import com.kos.sources.wowforever.WowForeverTestHelper.foreverCharacter
import com.kos.sources.wowforever.WowForeverTestHelper.foreverData
import com.kos.views.Game
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import kotlin.test.Test
import kotlin.test.assertEquals

class WowForeverEntitySynchronizerTest {
    private val blizzardClient = mock(BlizzardClient::class.java)

    private suspend fun givenMedia() =
        `when`(
            blizzardClient.getForeverCharacterMedia(
                basicWowForeverEntity.region,
                basicWowForeverEntity.name,
                basicWowForeverEntity.surname
            )
        ).thenReturn(Either.Right("avatar.jpg"))

    @Test
    fun `caches the profile and avatar of a wow forever character`() {
        runBlocking {
            `when`(
                blizzardClient.getForeverProfile(
                    basicWowForeverEntity.region,
                    basicWowForeverEntity.name,
                    basicWowForeverEntity.surname
                )
            ).thenReturn(Either.Right(foreverCharacter))
            givenMedia()

            val dataCacheRepository = DataCacheInMemoryRepository()
            val synchronizer = WowForeverEntitySynchronizer(dataCacheRepository, blizzardClient)

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            val cached = dataCacheRepository.get(basicWowForeverEntity.id).single()
            assertEquals(Game.WOW_FOREVER, cached.game)
            assertEquals(foreverData, synchronizer.json.decodeFromString<Data>(cached.data))
        }
    }

    @Test
    fun `a character whose profile could not be fetched is reported as a partial failure and not cached`() {
        runBlocking {
            val error = TimeoutError("timeout")
            `when`(
                blizzardClient.getForeverProfile(
                    basicWowForeverEntity.region,
                    basicWowForeverEntity.name,
                    basicWowForeverEntity.surname
                )
            ).thenReturn(Either.Left(error))

            val dataCacheRepository = DataCacheInMemoryRepository()
            val synchronizer = WowForeverEntitySynchronizer(dataCacheRepository, blizzardClient)

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(ThirdPartyCallFailed(basicWowForeverEntity.id, error)), errors)
            assertEquals(listOf(), dataCacheRepository.get(basicWowForeverEntity.id))
        }
    }
}

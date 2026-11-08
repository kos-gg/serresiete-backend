package com.kos.sources.wowforever

import arrow.core.Either
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.datacache.DataCache
import com.kos.datacache.domain.Data
import com.kos.datacache.domain.WowForeverData
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesTestHelper.basicWowForeverEntity
import com.kos.entities.ThirdPartyCallFailed
import com.kos.sources.wowforever.WowForeverTestHelper.belt
import com.kos.sources.wowforever.WowForeverTestHelper.boots
import com.kos.sources.wowforever.WowForeverTestHelper.bootsIcon
import com.kos.sources.wowforever.WowForeverTestHelper.foreverCharacter
import com.kos.sources.wowforever.WowForeverTestHelper.foreverData
import com.kos.sources.wowforever.media.WowForeverMedia
import com.kos.sources.wowforever.media.WowForeverMediaInMemoryRepository
import com.kos.sources.wowforever.media.WowForeverMediaKind.ITEM
import com.kos.sources.wowforever.media.WowForeverMediaStatus.*
import com.kos.views.Game
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.mockito.Mockito.*
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class WowForeverEntitySynchronizerTest {
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val dataCacheRepository = DataCacheInMemoryRepository()
    private val mediaRepository = WowForeverMediaInMemoryRepository()
    private val synchronizer = WowForeverEntitySynchronizer(dataCacheRepository, blizzardClient, mediaRepository)

    private val resolvedBoots = WowForeverMedia(ITEM, boots.details.id, "eu", bootsIcon, RESOLVED, 0)

    private suspend fun givenProfile() {
        `when`(
            blizzardClient.getForeverProfile(
                basicWowForeverEntity.region,
                basicWowForeverEntity.name,
                basicWowForeverEntity.surname
            )
        ).thenReturn(Either.Right(foreverCharacter))
    }

    private suspend fun givenEquipment(result: Either<TimeoutError, List<WowForeverEquippedItem>>) {
        `when`(
            blizzardClient.getForeverEquipment(
                basicWowForeverEntity.region,
                basicWowForeverEntity.name,
                basicWowForeverEntity.surname
            )
        ).thenReturn(result)
    }

    private suspend fun givenPreviousData(data: WowForeverData) {
        dataCacheRepository.withState(
            listOf(
                DataCache(
                    basicWowForeverEntity.id,
                    synchronizer.json.encodeToString<Data>(data),
                    OffsetDateTime.now().minusHours(1),
                    Game.WOW_FOREVER
                )
            )
        )
    }

    private suspend fun cachedData(): WowForeverData =
        synchronizer.json.decodeFromString<Data>(
            dataCacheRepository.get(basicWowForeverEntity.id).maxBy { it.inserted }.data
        ) as WowForeverData

    @Test
    fun `caches the equipment with the icons already resolved and registers the missing ones as pending`() {
        runBlocking {
            mediaRepository.withState(listOf(resolvedBoots))
            givenProfile()
            givenEquipment(Either.Right(listOf(boots, belt)))

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(Game.WOW_FOREVER, dataCacheRepository.get(basicWowForeverEntity.id).single().game)
            assertEquals(
                foreverData.copy(items = listOf(boots.toWowItem(bootsIcon), belt.toWowItem(null))),
                cachedData()
            )
            assertEquals(
                listOf(WowForeverMedia(ITEM, belt.details.id, "eu", null, PENDING, 0), resolvedBoots),
                mediaRepository.state()
            )
        }
    }

    @Test
    fun `an item whose icon is unavailable stays without icon and is not registered again`() {
        runBlocking {
            val unavailableBelt = WowForeverMedia(ITEM, belt.details.id, "eu", null, UNAVAILABLE, 0)
            mediaRepository.withState(listOf(unavailableBelt))
            givenProfile()
            givenEquipment(Either.Right(listOf(belt)))

            synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(belt.toWowItem(null)), cachedData().items)
            assertEquals(listOf(unavailableBelt), mediaRepository.state())
        }
    }

    @Test
    fun `when the equipment could not be fetched the previous items are kept and their missing icons filled`() {
        runBlocking {
            mediaRepository.withState(listOf(resolvedBoots))
            givenPreviousData(foreverData.copy(items = listOf(boots.toWowItem(null), belt.toWowItem(null))))
            givenProfile()
            givenEquipment(Either.Left(TimeoutError("timeout")))

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(2, dataCacheRepository.get(basicWowForeverEntity.id).size)
            assertEquals(listOf(boots.toWowItem(bootsIcon), belt.toWowItem(null)), cachedData().items)
            assertEquals(
                listOf(WowForeverMedia(ITEM, belt.details.id, "eu", null, PENDING, 0), resolvedBoots),
                mediaRepository.state()
            )
        }
    }

    @Test
    fun `when the equipment could not be fetched and there are no previous items, items are unknown`() {
        runBlocking {
            givenProfile()
            givenEquipment(Either.Left(TimeoutError("timeout")))

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(foreverData, cachedData())
            assertEquals(listOf(), mediaRepository.state())
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

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(ThirdPartyCallFailed(basicWowForeverEntity.id, error)), errors)
            assertEquals(listOf(), dataCacheRepository.get(basicWowForeverEntity.id))
            verify(blizzardClient, never()).getForeverEquipment(
                basicWowForeverEntity.region,
                basicWowForeverEntity.name,
                basicWowForeverEntity.surname
            )
        }
    }
}

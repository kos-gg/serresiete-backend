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
import com.kos.entities.domain.WowForeverEntity
import com.kos.sources.wowforever.WowForeverTestHelper.belt
import com.kos.sources.wowforever.WowForeverTestHelper.boots
import com.kos.sources.wowforever.WowForeverTestHelper.bootsIcon
import com.kos.sources.wowforever.WowForeverTestHelper.foreverCharacter
import com.kos.sources.wowforever.WowForeverTestHelper.foreverData
import com.kos.sources.wowforever.media.WowForeverMedia
import com.kos.sources.wowforever.media.WowForeverMediaInMemoryRepository
import com.kos.sources.wowforever.media.WowForeverMediaKind.ITEM
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

    private val beltIcon = "https://render.worldofwarcraft.com/forever-eu/icons/56/belt.jpg"
    private val storedBoots = WowForeverMedia(ITEM, boots.details.id, bootsIcon)
    private val otherEntity = basicWowForeverEntity.copy(id = 2, name = "otro", blizzardId = 2)

    private suspend fun givenProfile(entity: WowForeverEntity = basicWowForeverEntity) {
        `when`(blizzardClient.getForeverProfile(entity.region, entity.name, entity.surname))
            .thenReturn(Either.Right(foreverCharacter))
    }

    private suspend fun givenEquipment(
        result: Either<TimeoutError, List<WowForeverEquippedItem>>,
        entity: WowForeverEntity = basicWowForeverEntity
    ) {
        `when`(blizzardClient.getForeverEquipment(entity.region, entity.name, entity.surname)).thenReturn(result)
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

    private suspend fun cachedData(entityId: Long = basicWowForeverEntity.id): WowForeverData =
        synchronizer.json.decodeFromString<Data>(
            dataCacheRepository.get(entityId).maxBy { it.inserted }.data
        ) as WowForeverData

    @Test
    fun `caches the equipment using stored icons and fetching and storing the missing ones`() {
        runBlocking {
            mediaRepository.withState(listOf(storedBoots))
            givenProfile()
            givenEquipment(Either.Right(listOf(boots, belt)))
            `when`(blizzardClient.getForeverItemMedia("eu", belt.details.id)).thenReturn(Either.Right(beltIcon))

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(Game.WOW_FOREVER, dataCacheRepository.get(basicWowForeverEntity.id).single().game)
            assertEquals(
                foreverData.copy(items = listOf(boots.toWowItem(bootsIcon), belt.toWowItem(beltIcon))),
                cachedData()
            )
            verify(blizzardClient, never()).getForeverItemMedia("eu", boots.details.id)
            assertEquals(listOf(WowForeverMedia(ITEM, belt.details.id, beltIcon), storedBoots), mediaRepository.state())
        }
    }

    @Test
    fun `an item worn by several characters of the same sync is fetched only once`() {
        runBlocking {
            givenProfile()
            givenProfile(otherEntity)
            givenEquipment(Either.Right(listOf(belt)))
            givenEquipment(Either.Right(listOf(belt)), otherEntity)
            `when`(blizzardClient.getForeverItemMedia("eu", belt.details.id)).thenReturn(Either.Right(beltIcon))

            synchronizer.synchronize(listOf(basicWowForeverEntity, otherEntity))

            verify(blizzardClient, times(1)).getForeverItemMedia("eu", belt.details.id)
            assertEquals(listOf(belt.toWowItem(beltIcon)), cachedData().items)
            assertEquals(listOf(belt.toWowItem(beltIcon)), cachedData(otherEntity.id).items)
        }
    }

    @Test
    fun `an icon that could not be fetched is left empty and not stored`() {
        runBlocking {
            givenProfile()
            givenEquipment(Either.Right(listOf(belt)))
            `when`(blizzardClient.getForeverItemMedia("eu", belt.details.id))
                .thenReturn(Either.Left(TimeoutError("timeout")))

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(listOf(belt.toWowItem(null)), cachedData().items)
            assertEquals(listOf(), mediaRepository.state())
        }
    }

    @Test
    fun `when the equipment could not be fetched the previous items are kept and their missing icons filled`() {
        runBlocking {
            mediaRepository.withState(listOf(storedBoots))
            givenPreviousData(foreverData.copy(items = listOf(boots.toWowItem(null), belt.toWowItem(null))))
            givenProfile()
            givenEquipment(Either.Left(TimeoutError("timeout")))
            `when`(blizzardClient.getForeverItemMedia("eu", belt.details.id)).thenReturn(Either.Right(beltIcon))

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(2, dataCacheRepository.get(basicWowForeverEntity.id).size)
            assertEquals(listOf(boots.toWowItem(bootsIcon), belt.toWowItem(beltIcon)), cachedData().items)
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

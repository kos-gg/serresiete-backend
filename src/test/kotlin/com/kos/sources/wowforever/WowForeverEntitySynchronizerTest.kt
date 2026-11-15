package com.kos.sources.wowforever

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.NotFound
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.datacache.DataCache
import com.kos.datacache.domain.Data
import com.kos.datacache.domain.WowForeverData
import com.kos.datacache.domain.WowForeverProfession
import com.kos.datacache.domain.WowForeverProfessions
import com.kos.datacache.domain.WowForeverPvp
import com.kos.datacache.domain.WowForeverSpecialization
import com.kos.datacache.domain.WowStats
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesTestHelper.basicWowForeverEntity
import com.kos.entities.ThirdPartyCallFailed
import com.kos.entities.domain.WowForeverEntity
import com.kos.sources.wowforever.WowForeverTestHelper.arms
import com.kos.sources.wowforever.WowForeverTestHelper.belt
import com.kos.sources.wowforever.WowForeverTestHelper.boots
import com.kos.sources.wowforever.WowForeverTestHelper.bootsIcon
import com.kos.sources.wowforever.WowForeverTestHelper.foreverAvatar
import com.kos.sources.wowforever.WowForeverTestHelper.foreverCharacter
import com.kos.sources.wowforever.WowForeverTestHelper.foreverData
import com.kos.sources.wowforever.WowForeverTestHelper.foreverPvp
import com.kos.sources.wowforever.WowForeverTestHelper.foreverStats
import com.kos.sources.wowforever.WowForeverTestHelper.heroicStrike
import com.kos.sources.wowforever.WowForeverTestHelper.heroicStrikeIcon
import com.kos.sources.wowforever.WowForeverTestHelper.noProfessions
import com.kos.sources.wowforever.WowForeverTestHelper.rend
import com.kos.sources.wowforever.media.WowForeverMedia
import com.kos.sources.wowforever.media.WowForeverMediaInMemoryRepository
import com.kos.sources.wowforever.media.WowForeverMediaKind.ITEM
import com.kos.sources.wowforever.media.WowForeverMediaKind.PROFESSION
import com.kos.sources.wowforever.media.WowForeverMediaKind.SPELL
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
    private val rendIcon = "https://render.worldofwarcraft.com/forever-eu/icons/56/rend.jpg"
    private val storedBoots = WowForeverMedia(ITEM, boots.details.id, bootsIcon)
    private val otherEntity = basicWowForeverEntity.copy(id = 2, name = "otro", blizzardId = 2)
    private val previousData = foreverData.copy(lastLogin = foreverData.lastLogin.minusDays(1))
    private val mining = WowForeverProfession(186, "Mining", 202, 225)
    private val cooking = WowForeverProfession(185, "Cooking", 4, 75)
    private val miningIcon = "https://render.worldofwarcraft.com/forever-eu/icons/56/136248.jpg"
    private val cookingIcon = "https://render.worldofwarcraft.com/forever-eu/icons/56/cooking.jpg"

    private suspend fun givenProfile(entity: WowForeverEntity = basicWowForeverEntity) {
        `when`(blizzardClient.getForeverProfile(entity.region, entity.name, entity.surname))
            .thenReturn(Either.Right(foreverCharacter))
        givenEquipment(Either.Right(listOf()), entity)
        givenSpecializations(Either.Right(listOf()), entity)
        givenStatistics(Either.Right(foreverStats), entity)
        givenAvatar(Either.Right(foreverAvatar), entity)
        givenPvp(Either.Right(foreverPvp), entity)
        givenProfessions(Either.Right(noProfessions), entity)
    }

    private suspend fun givenProfessions(
        result: Either<ClientError, WowForeverProfessions>,
        entity: WowForeverEntity = basicWowForeverEntity
    ) {
        `when`(blizzardClient.getForeverProfessions(entity.region, entity.name, entity.surname)).thenReturn(result)
    }

    private suspend fun givenPvp(
        result: Either<ClientError, WowForeverPvp>,
        entity: WowForeverEntity = basicWowForeverEntity
    ) {
        `when`(blizzardClient.getForeverPvpSummary(entity.region, entity.name, entity.surname)).thenReturn(result)
    }

    private suspend fun givenAvatar(
        result: Either<ClientError, String?>,
        entity: WowForeverEntity = basicWowForeverEntity
    ) {
        `when`(blizzardClient.getForeverAvatar(entity.region, entity.name, entity.surname)).thenReturn(result)
    }

    private suspend fun givenStatistics(
        result: Either<TimeoutError, WowStats>,
        entity: WowForeverEntity = basicWowForeverEntity
    ) {
        `when`(blizzardClient.getForeverStatistics(entity.region, entity.name, entity.surname)).thenReturn(result)
    }

    private suspend fun givenSpecializations(
        result: Either<TimeoutError, List<WowForeverSpecialization>>,
        entity: WowForeverEntity = basicWowForeverEntity
    ) {
        `when`(blizzardClient.getForeverSpecializations(entity.region, entity.name, entity.surname)).thenReturn(result)
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
                foreverData.copy(
                    items = listOf(boots.toWowItem(bootsIcon), belt.toWowItem(beltIcon)),
                    talents = listOf()
                ),
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
            givenPreviousData(previousData.copy(items = listOf(boots.toWowItem(null), belt.toWowItem(null))))
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
            assertEquals(foreverData.copy(talents = listOf()), cachedData())
        }
    }

    @Test
    fun `caches the talents using stored icons and fetching and storing the missing ones`() {
        runBlocking {
            val storedHeroicStrike = WowForeverMedia(SPELL, heroicStrike.spellId, heroicStrikeIcon)
            mediaRepository.withState(listOf(storedHeroicStrike))
            givenProfile()
            givenSpecializations(Either.Right(listOf(arms(heroicStrike, rend))))
            `when`(blizzardClient.getForeverSpellMedia("eu", rend.spellId)).thenReturn(Either.Right(rendIcon))

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(
                listOf(arms(heroicStrike.copy(icon = heroicStrikeIcon), rend.copy(icon = rendIcon))),
                cachedData().talents
            )
            verify(blizzardClient, never()).getForeverSpellMedia("eu", heroicStrike.spellId)
            assertEquals(
                listOf(storedHeroicStrike, WowForeverMedia(SPELL, rend.spellId, rendIcon)),
                mediaRepository.state()
            )
        }
    }

    @Test
    fun `a talent shared by several characters of the same sync is fetched only once`() {
        runBlocking {
            givenProfile()
            givenProfile(otherEntity)
            givenSpecializations(Either.Right(listOf(arms(rend))))
            givenSpecializations(Either.Right(listOf(arms(rend))), otherEntity)
            `when`(blizzardClient.getForeverSpellMedia("eu", rend.spellId)).thenReturn(Either.Right(rendIcon))

            synchronizer.synchronize(listOf(basicWowForeverEntity, otherEntity))

            verify(blizzardClient, times(1)).getForeverSpellMedia("eu", rend.spellId)
            assertEquals(listOf(arms(rend.copy(icon = rendIcon))), cachedData().talents)
            assertEquals(listOf(arms(rend.copy(icon = rendIcon))), cachedData(otherEntity.id).talents)
        }
    }

    @Test
    fun `an item and a spell with the same id are different icons`() {
        runBlocking {
            val sameIdSpell = rend.copy(spellId = belt.details.id)
            givenProfile()
            givenEquipment(Either.Right(listOf(belt)))
            givenSpecializations(Either.Right(listOf(arms(sameIdSpell))))
            `when`(blizzardClient.getForeverItemMedia("eu", belt.details.id)).thenReturn(Either.Right(beltIcon))
            `when`(blizzardClient.getForeverSpellMedia("eu", belt.details.id)).thenReturn(Either.Right(rendIcon))

            synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(belt.toWowItem(beltIcon)), cachedData().items)
            assertEquals(listOf(arms(sameIdSpell.copy(icon = rendIcon))), cachedData().talents)
        }
    }

    @Test
    fun `when the specializations could not be fetched the previous talents are kept and their missing icons filled`() {
        runBlocking {
            givenPreviousData(previousData.copy(talents = listOf(arms(rend))))
            givenProfile()
            givenSpecializations(Either.Left(TimeoutError("timeout")))
            `when`(blizzardClient.getForeverSpellMedia("eu", rend.spellId)).thenReturn(Either.Right(rendIcon))

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(listOf(arms(rend.copy(icon = rendIcon))), cachedData().talents)
        }
    }

    @Test
    fun `when the specializations could not be fetched and there are no previous talents, talents are unknown`() {
        runBlocking {
            givenProfile()
            givenSpecializations(Either.Left(TimeoutError("timeout")))

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(null, cachedData().talents)
        }
    }

    @Test
    fun `when the statistics could not be fetched the previous statistics are kept`() {
        runBlocking {
            val previousStats = foreverStats.copy(health = 500)
            givenPreviousData(previousData.copy(stats = previousStats))
            givenProfile()
            givenStatistics(Either.Left(TimeoutError("timeout")))

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(previousStats, cachedData().stats)
        }
    }

    @Test
    fun `when the statistics could not be fetched and there are no previous statistics, statistics are unknown`() {
        runBlocking {
            givenProfile()
            givenStatistics(Either.Left(TimeoutError("timeout")))

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(null, cachedData().stats)
        }
    }

    @Test
    fun `when the avatar could not be fetched the previous avatar is kept`() {
        runBlocking {
            val previousAvatar = "https://render.worldofwarcraft.com/forever-eu/character/137/previous-avatar-sd.jpg"
            givenPreviousData(previousData.copy(avatar = previousAvatar))
            givenProfile()
            givenAvatar(Either.Left(TimeoutError("timeout")))

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(previousAvatar, cachedData().avatar)
        }
    }

    @Test
    fun `when the pvp summary could not be fetched the previous pvp summary is kept`() {
        runBlocking {
            val previousPvp = WowForeverPvp(honorableKills = 5, rank = 0)
            givenPreviousData(previousData.copy(pvp = previousPvp))
            givenProfile()
            givenPvp(Either.Left(TimeoutError("timeout")))

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(previousPvp, cachedData().pvp)
            assertEquals(previousData.lastLogin, cachedData().lastLogin)
        }
    }

    @Test
    fun `a character cached before the pvp summary existed gets it on its next logout`() {
        runBlocking {
            givenPreviousData(previousData.copy(pvp = null))
            givenProfile()

            synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(foreverPvp, cachedData().pvp)
        }
    }

    @Test
    fun `caches the professions using stored icons and fetching and storing the missing ones`() {
        runBlocking {
            val storedMining = WowForeverMedia(PROFESSION, mining.id, miningIcon)
            mediaRepository.withState(listOf(storedMining))
            givenProfile()
            givenProfessions(Either.Right(WowForeverProfessions(primaries = listOf(mining), secondaries = listOf(cooking))))
            `when`(blizzardClient.getForeverProfessionMedia("eu", cooking.id)).thenReturn(Either.Right(cookingIcon))

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(
                WowForeverProfessions(
                    primaries = listOf(mining.copy(icon = miningIcon)),
                    secondaries = listOf(cooking.copy(icon = cookingIcon))
                ),
                cachedData().professions
            )
            verify(blizzardClient, never()).getForeverProfessionMedia("eu", mining.id)
            assertEquals(
                listOf(WowForeverMedia(PROFESSION, cooking.id, cookingIcon), storedMining),
                mediaRepository.state()
            )
        }
    }

    @Test
    fun `when the professions could not be fetched the previous professions are kept`() {
        runBlocking {
            val previousProfessions = WowForeverProfessions(primaries = listOf(mining.copy(icon = miningIcon)), secondaries = listOf())
            mediaRepository.withState(listOf(WowForeverMedia(PROFESSION, mining.id, miningIcon)))
            givenPreviousData(previousData.copy(professions = previousProfessions))
            givenProfile()
            givenProfessions(Either.Left(TimeoutError("timeout")))

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(previousProfessions, cachedData().professions)
            assertEquals(previousData.lastLogin, cachedData().lastLogin)
        }
    }

    @Test
    fun `a character cached before the professions existed gets them on its next logout`() {
        runBlocking {
            givenPreviousData(previousData.copy(professions = null))
            givenProfile()

            synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(noProfessions, cachedData().professions)
        }
    }

    @Test
    fun `a character that has not logged out since the last sync reuses its cached details`() {
        runBlocking {
            val cachedItems = listOf(boots.toWowItem(bootsIcon))
            mediaRepository.withState(listOf(storedBoots))
            givenPreviousData(foreverData.copy(items = cachedItems, talents = listOf(), avatar = null))
            givenProfile()

            val errors = synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(listOf(), errors)
            assertEquals(2, dataCacheRepository.get(basicWowForeverEntity.id).size)
            assertEquals(foreverData.copy(items = cachedItems, talents = listOf(), avatar = null), cachedData())
            verify(blizzardClient, never()).getForeverEquipment(
                basicWowForeverEntity.region,
                basicWowForeverEntity.name,
                basicWowForeverEntity.surname
            )
            verify(blizzardClient, never()).getForeverSpecializations(
                basicWowForeverEntity.region,
                basicWowForeverEntity.name,
                basicWowForeverEntity.surname
            )
            verify(blizzardClient, never()).getForeverStatistics(
                basicWowForeverEntity.region,
                basicWowForeverEntity.name,
                basicWowForeverEntity.surname
            )
            verify(blizzardClient, never()).getForeverAvatar(
                basicWowForeverEntity.region,
                basicWowForeverEntity.name,
                basicWowForeverEntity.surname
            )
            verify(blizzardClient, never()).getForeverPvpSummary(
                basicWowForeverEntity.region,
                basicWowForeverEntity.name,
                basicWowForeverEntity.surname
            )
            verify(blizzardClient, never()).getForeverProfessions(
                basicWowForeverEntity.region,
                basicWowForeverEntity.name,
                basicWowForeverEntity.surname
            )
        }
    }

    @Test
    fun `a character that logged out since the last sync fetches its details`() {
        runBlocking {
            givenPreviousData(previousData.copy(items = listOf(boots.toWowItem(bootsIcon))))
            givenProfile()
            givenEquipment(Either.Right(listOf(belt)))
            `when`(blizzardClient.getForeverItemMedia("eu", belt.details.id)).thenReturn(Either.Right(beltIcon))

            synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(foreverData.copy(items = listOf(belt.toWowItem(beltIcon)), talents = listOf()), cachedData())
        }
    }

    @Test
    fun `when a detail could not be fetched the previous login is kept so the next sync fetches the details again`() {
        runBlocking {
            givenPreviousData(previousData)
            givenProfile()
            givenStatistics(Either.Left(TimeoutError("timeout")))

            synchronizer.synchronize(listOf(basicWowForeverEntity))
            assertEquals(previousData.lastLogin, cachedData().lastLogin)

            givenStatistics(Either.Right(foreverStats))
            synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(foreverData.copy(items = listOf(), talents = listOf()), cachedData())
        }
    }

    @Test
    fun `a character without avatar is not a failure`() {
        runBlocking {
            givenPreviousData(previousData.copy(avatar = null))
            givenProfile()
            givenAvatar(Either.Left(NotFound))

            synchronizer.synchronize(listOf(basicWowForeverEntity))

            assertEquals(foreverData.copy(items = listOf(), talents = listOf(), avatar = null), cachedData())
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

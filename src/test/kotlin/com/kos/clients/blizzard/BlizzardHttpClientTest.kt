package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.JsonParseError
import com.kos.clients.NotFound
import com.kos.clients.blizzard.BlizzardHttpClientHelper.client
import com.kos.clients.config.RetryConfig
import com.kos.clients.config.blizzardHttpClientConfig
import com.kos.datacache.BlizzardMockHelper
import com.kos.datacache.BlizzardMockHelper.getWowCharacterResponse
import com.kos.datacache.domain.ClassSpec
import com.kos.datacache.domain.RunDetails
import com.kos.datacache.domain.RunMember
import com.kos.datacache.domain.WowForeverSpecialization
import com.kos.datacache.domain.WowForeverTalent
import com.kos.datacache.domain.WowPrice
import com.kos.datacache.domain.WowResistances
import com.kos.datacache.domain.WowResource
import com.kos.datacache.domain.WowStats
import com.kos.datacache.domain.WowWeaponStats
import com.kos.datacache.domain.WowWeaponDisplayableStats
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.domain.WowCharacter
import com.kos.entities.domain.WowForeverCharacter
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import com.kos.sources.wow.KeystoneBestRun
import com.kos.sources.wow.MythicKeystoneProfile
import com.kos.sources.wowforever.WowForeverEquippedItem
import com.kos.sources.wowhc.WowEquippedItemRef
import com.kos.sources.wowhc.WowItemDetails
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlizzardHttpClientTest {

    private val blizzardAuthClient = mock(BlizzardAuthClient::class.java)
    private val blizzardClient = BlizzardHttpClient(HttpClient(client.engine) {
        blizzardHttpClientConfig(
            RetryConfig(0, 0),
            blizzardAuthClient
        )
    })

    @Test
    fun `getCharacterProfile returns successful response`() {
        runBlocking {
            givenAValidToken()
            val result: Either<ClientError, WowCharacter?> = blizzardClient.getClassicProfile(
                "region", "realm", "name"
            )
            assertEquals(Either.Right(getWowCharacterResponse.toWowCharacter()), result)
        }
    }

    @Test
    fun `getClassicProfile returns not found when blizzard doesn't find the character`() = runBlocking {
        givenAValidToken()

        assertEquals(Either.Left(NotFound), blizzardClient.getClassicProfile("region", "realm", "unknown"))
    }

    @Test
    fun `getRetailProfile returns not found when blizzard doesn't find the character`() = runBlocking {
        givenAValidToken()

        assertEquals(Either.Left(NotFound), blizzardClient.getRetailProfile("eu", "realm", "unknown"))
    }

    @Test
    fun `getRetailGuildRoster returns not found when blizzard doesn't find the guild`() = runBlocking {
        givenAValidToken()

        assertEquals(Either.Left(NotFound), blizzardClient.getRetailGuildRoster("us", "realm", "unknown"))
    }

    @Test
    fun `getCharacterMedia returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getCharacterMedia("region", "realm", "name")

        assertEquals(
            BlizzardMockHelper.getCharacterMedia(basicWowEntity),
            result
        )
    }

    @Test
    fun `getCharacterEquipment returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getCharacterEquipment("region", "realm", "name")

        assertEquals(
            Either.Right(BlizzardMockHelper.getWowEquipmentResponse.toEquippedItemRefs()),
            result
        )
    }

    @Test
    fun `getCharacterSpecializations returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getCharacterSpecializations("region", "realm", "name")

        assertEquals(
            Either.Right(BlizzardMockHelper.getWowSpecializationsResponse.toActiveSpecializations()),
            result
        )
    }

    @Test
    fun `getCharacterStats returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getCharacterStats("region", "realm", "name")

        assertEquals(
            Either.Right(BlizzardMockHelper.getWowStatsResponse.toWowStats()),
            result
        )
    }

    @Test
    fun `getItemMedia returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getItemMedia("region", 123L)

        assertEquals(
            BlizzardMockHelper.getItemMedia(),
            result
        )
    }

    @Test
    fun `getItem returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getItem("region", 18421)

        assertEquals(
            Either.Right(BlizzardMockHelper.getWowItemResponse.toWowItemDetails()),
            result
        )
    }

    @Test
    fun `getRealm returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getRealm("region", 1L)

        assertEquals(
            Either.Right(BlizzardMockHelper.notHardcoreRealm.toWowRealm()),
            result
        )
    }

    @Test
    fun `getGuildRoster returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getHardcoreGuildRoster("region", "realm", "guild")

        assertEquals(
            Either.Right(BlizzardMockHelper.getWowGuildRosterResponse.toGuildRoster()),
            result
        )
    }


    @Test
    fun `getRetailGuildRoster returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getRetailGuildRoster("us", "realm", "guild")

        assertEquals(Either.Right(BlizzardMockHelper.getWowRetailGuildRosterResponse.toGuildRoster()), result)
    }

    @Test
    fun `getMythicKeystoneProfile returns the rating and the members of each best run`() = runBlocking {
        givenAValidToken()

        val sanxei = RunMember("Sanxei", "zuljin", "Mage", ClassSpec(62, "Arcane", "dps"), "Dwarf", 328)
        val sanxeiTank = RunMember("Sanxeitank", "kazzak", "Paladin", ClassSpec(66, "Protection", "tank"), "Human", 330)

        assertEquals(
            Either.Right(
                MythicKeystoneProfile(
                    rating = 3521.2056,
                    bestRuns = listOf(
                        KeystoneBestRun(249, 11, false, RunDetails(listOf(sanxei.copy(itemLevel = 292)))),
                        KeystoneBestRun(249, 20, true, RunDetails(listOf(sanxei, sanxeiTank)))
                    )
                )
            ),
            blizzardClient.getMythicKeystoneProfile("eu", "realm", "name", 16)
        )
    }

    @Test
    fun `getMythicKeystoneProfile returns no rating and no runs when the character has not played the season`() =
        runBlocking {
            givenAValidToken()

            assertEquals(
                Either.Right(MythicKeystoneProfile(0.0, emptyList())),
                blizzardClient.getMythicKeystoneProfile("eu", "realm", "unrated", 16)
            )
        }

    @Test
    fun `getMythicKeystoneProfile returns not found when blizzard doesn't find the character`() =
        runBlocking {
            givenAValidToken()

            assertEquals(
                Either.Left(NotFound),
                blizzardClient.getMythicKeystoneProfile("eu", "realm", "unknown", 16)
            )
        }

    @Test
    fun `getMythicKeystoneProfile fails when a member has a spec we don't know`() = runBlocking {
        givenAValidToken()

        val result = blizzardClient.getMythicKeystoneProfile("eu", "realm", "unknownspec", 16)

        assertTrue(result.isLeft { it is JsonParseError })
    }

    @Test
    fun `getForeverProfile fetches the character by name and surname from the forever namespace`() = runBlocking {
        givenAValidToken()

        val expected = WowForeverCharacter(
            id = 6880137,
            name = "Siempre",
            surname = "Tarde",
            level = 14,
            averageItemLevel = 8,
            equippedItemLevel = 8,
            characterClass = "Warrior",
            faction = "Horde",
            race = "Orc",
            gender = "Female",
            guild = "TSALAB",
            experience = 11355,
            ruleset = "PvP",
            lastLogin = OffsetDateTime.ofInstant(Instant.ofEpochMilli(1793926086000), ZoneId.systemDefault())
        )

        assertEquals(Either.Right(expected), blizzardClient.getForeverProfile("eu", "Siempre", "Tarde"))
    }

    @Test
    fun `getForeverProfile returns not found when blizzard doesn't find the character`() = runBlocking {
        givenAValidToken()

        assertEquals(Either.Left(NotFound), blizzardClient.getForeverProfile("eu", "unknown", "tarde"))
    }

    @Test
    fun `getForeverEquipment maps every equipped item with its details from the forever namespace`() = runBlocking {
        givenAValidToken()

        val expected = listOf(
            WowForeverEquippedItem(
                WowEquippedItemRef(6125, "Shirt", listOf()),
                WowItemDetails(
                    id = 6125,
                    quality = "Common",
                    name = "Brawler's Harness",
                    level = 1,
                    binding = null,
                    requiredLevel = 0,
                    itemSubclass = "Miscellaneous",
                    armor = null,
                    stats = listOf(),
                    spells = listOf(),
                    sellPrice = WowPrice("Sell Price:", "0", "0", "1"),
                    durability = null,
                    weaponStats = null
                )
            ),
            WowForeverEquippedItem(
                WowEquippedItemRef(15487, "Chest", listOf()),
                WowItemDetails(
                    id = 15487,
                    quality = "Uncommon",
                    name = "War Torn Tunic of Stamina",
                    level = 16,
                    binding = "Binds when equipped",
                    requiredLevel = 11,
                    itemSubclass = "Mail",
                    armor = "151 Armor",
                    stats = listOf("+4 Stamina"),
                    spells = listOf(),
                    sellPrice = WowPrice("Sell Price:", "0", "4", "79"),
                    durability = "Durability 50 / 75",
                    weaponStats = null
                )
            ),
            WowForeverEquippedItem(
                WowEquippedItemRef(6350, "Feet", listOf("Enchanted: Stamina +1 and Armor +8")),
                WowItemDetails(
                    id = 6350,
                    quality = "Uncommon",
                    name = "Rough Bronze Boots",
                    level = 18,
                    binding = "Binds when equipped",
                    requiredLevel = 13,
                    itemSubclass = "Mail",
                    armor = "111 Armor",
                    stats = listOf("+2 Strength", "+3 Stamina"),
                    spells = listOf(),
                    sellPrice = WowPrice("Sell Price:", "0", "2", "95"),
                    durability = "Durability 30 / 40",
                    weaponStats = null
                )
            ),
            WowForeverEquippedItem(
                WowEquippedItemRef(15424, "Main Hand", listOf()),
                WowItemDetails(
                    id = 15424,
                    quality = "Rare",
                    name = "Axe of Orgrimmar",
                    level = 18,
                    binding = "Binds when picked up",
                    requiredLevel = 0,
                    itemSubclass = "Axe",
                    armor = null,
                    stats = listOf("+6 Stamina", "+6 Spirit"),
                    spells = listOf(),
                    sellPrice = WowPrice("Sell Price:", "0", "16", "14"),
                    durability = "Durability 54 / 70",
                    weaponStats = WowWeaponDisplayableStats("42 - 64 Damage", "(16.1 damage per second)", "Speed 3.30")
                )
            )
        )

        assertEquals(Either.Right(expected), blizzardClient.getForeverEquipment("eu", "Siempre", "Tarde"))
    }

    @Test
    fun `getForeverEquipment returns not found when blizzard doesn't find the character`() = runBlocking {
        givenAValidToken()

        assertEquals(Either.Left(NotFound), blizzardClient.getForeverEquipment("eu", "unknown", "tarde"))
    }

    @Test
    fun `getForeverSpecializations maps the talents of the active specialization group`() = runBlocking {
        givenAValidToken()

        val expected = listOf(
            WowForeverSpecialization(
                name = "Arms",
                points = 5,
                talents = listOf(
                    WowForeverTalent(
                        name = "Improved Heroic Strike",
                        spellId = 12282,
                        rank = 2,
                        description = "Reduces the cost of your Heroic Strike ability by 2 Rage.",
                        icon = null
                    ),
                    WowForeverTalent(
                        name = "Improved Rend",
                        spellId = 12286,
                        rank = 3,
                        description = "Increases the Bleed damage done by your Rend ability by 35%.",
                        icon = null
                    )
                )
            )
        )

        assertEquals(Either.Right(expected), blizzardClient.getForeverSpecializations("eu", "Siempre", "Tarde"))
    }

    @Test
    fun `getForeverSpecializations returns not found when blizzard doesn't find the character`() = runBlocking {
        givenAValidToken()

        assertEquals(Either.Left(NotFound), blizzardClient.getForeverSpecializations("eu", "unknown", "tarde"))
    }

    @Test
    fun `getForeverStatistics maps the statistics`() = runBlocking {
        givenAValidToken()

        val expected = WowStats(
            health = 794,
            resource = WowResource("Rage", 100),
            strength = 91,
            agility = 36,
            intellect = 20,
            stamina = 75,
            meleeCrit = 4.5,
            attackPower = 228,
            mainHandStats = WowWeaponStats(97.85715, 122.85715, 3.0, 36.785717),
            offHandStats = WowWeaponStats(16.785715, 16.785715, 2.0, 8.392858),
            spellPower = 0.0,
            spellPenetration = 0.0,
            spellCrit = 0.0,
            manaRegen = 0.0,
            manaRegenCombat = 0.0,
            armor = 835,
            dodge = 4.5,
            parry = 5.0,
            block = 5.0,
            rangedCrit = 1.9399998,
            spirit = 30,
            defense = 110,
            resistances = WowResistances(fire = 0, shadow = 5, nature = 0, arcane = 0, frost = 0)
        )

        assertEquals(Either.Right(expected), blizzardClient.getForeverStatistics("eu", "Siempre", "Tarde"))
    }

    @Test
    fun `getForeverStatistics returns not found when blizzard doesn't find the character`() = runBlocking {
        givenAValidToken()

        assertEquals(Either.Left(NotFound), blizzardClient.getForeverStatistics("eu", "unknown", "tarde"))
    }

    @Test
    fun `getForeverAvatar returns the sd avatar`() = runBlocking {
        givenAValidToken()

        assertEquals(
            Either.Right("https://render.worldofwarcraft.com/forever-eu/character/137/6880137-avatar-sd.jpg"),
            blizzardClient.getForeverAvatar("eu", "Siempre", "Tarde")
        )
    }

    @Test
    fun `getForeverAvatar returns not found when blizzard doesn't find the character`() = runBlocking {
        givenAValidToken()

        assertEquals(Either.Left(NotFound), blizzardClient.getForeverAvatar("eu", "unknown", "tarde"))
    }

    @Test
    fun `getForeverSpellMedia fetches the icon from the forever static namespace`() = runBlocking {
        givenAValidToken()

        assertEquals(Either.Right("1.jpg"), blizzardClient.getForeverSpellMedia("eu", 12282))
    }

    @Test
    fun `getForeverItemMedia fetches the icon from the forever static namespace`() = runBlocking {
        givenAValidToken()

        assertEquals(Either.Right("1.jpg"), blizzardClient.getForeverItemMedia("eu", 6125))
    }

    private suspend fun givenAValidToken() {
        `when`(blizzardAuthClient.getAccessToken())
            .thenReturn(BlizzardMockHelper.getToken())
    }
}

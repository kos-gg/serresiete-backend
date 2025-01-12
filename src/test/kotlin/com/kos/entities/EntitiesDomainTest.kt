package com.kos.entities

import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicLolEntityEnrichedRequest
import com.kos.entities.EntitiesTestHelper.basicWowCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntitiesDomainTest {

    @Test
    fun `i can find every wow spec`() {
        val warriorSpecs = basicWowCharacter.specsWithName("Warrior").map { it.name }.toSet()
        val paladinSpecs = basicWowCharacter.specsWithName("Paladin").map { it.name }.toSet()
        val hunterSpecs = basicWowCharacter.specsWithName("Hunter").map { it.name }.toSet()
        val rogueSpecs = basicWowCharacter.specsWithName("Rogue").map { it.name }.toSet()
        val priestSpecs = basicWowCharacter.specsWithName("Priest").map { it.name }.toSet()
        val shamanSpecs = basicWowCharacter.specsWithName("Shaman").map { it.name }.toSet()
        val mageSpecs = basicWowCharacter.specsWithName("Mage").map { it.name }.toSet()
        val warlockSpecs = basicWowCharacter.specsWithName("Warlock").map { it.name }.toSet()
        val monkSpecs = basicWowCharacter.specsWithName("Monk").map { it.name }.toSet()
        val druidSpecs = basicWowCharacter.specsWithName("Druid").map { it.name }.toSet()
        val demonHunterSpecs = basicWowCharacter.specsWithName("Demon Hunter").map { it.name }.toSet()
        val deathKnightSpecs = basicWowCharacter.specsWithName("Death Knight").map { it.name }.toSet()
        val evokerSpecs = basicWowCharacter.specsWithName("Evoker").map { it.name }.toSet()

        val expectedWarriorSpecs = setOf("Protection Warrior", "Arms", "Fury")
        val expectedPaladinSpecs = setOf("Protection Paladin", "Retribution", "Holy Paladin")
        val expectedHunterSpecs = setOf("Beast Mastery", "Survival", "Marksmanship")
        val expectedRogueSpecs = setOf("Outlaw", "Assassination", "Subtlety")
        val expectedPriestSpecs = setOf("Shadow", "Holy Priest", "Discipline")
        val expectedShamanSpecs = setOf("Enhancement", "Elemental", "Restoration Shaman")
        val expectedMageSpecs = setOf("Arcane", "Fire", "Frost Mage")
        val expectedWarlockSpecs = setOf("Affliction", "Demonology", "Destruction")
        val expectedMonkSpecs = setOf("Wind Walker", "Brew Master", "Mist Weaver")
        val expectedDruidSpecs = setOf("Guardian", "Balance", "Feral", "Restoration Druid")
        val expectedDemonHunterSpecs = setOf("Havoc", "Vengeance")
        val expectedDeathKnightSpecs = setOf("Blood", "Frost Death Knight", "Unholy")
        val expectedEvokerSpecs = setOf("Devastation", "Preservation", "Augmentation")

        val specs = listOf(
            warriorSpecs,
            paladinSpecs,
            hunterSpecs,
            rogueSpecs,
            priestSpecs,
            shamanSpecs,
            mageSpecs,
            warlockSpecs,
            monkSpecs,
            druidSpecs,
            demonHunterSpecs,
            deathKnightSpecs,
            evokerSpecs
        )

        val expectedSpecs = listOf(
            expectedWarriorSpecs,
            expectedPaladinSpecs,
            expectedHunterSpecs,
            expectedRogueSpecs,
            expectedPriestSpecs,
            expectedShamanSpecs,
            expectedMageSpecs,
            expectedWarlockSpecs,
            expectedMonkSpecs,
            expectedDruidSpecs,
            expectedDemonHunterSpecs,
            expectedDeathKnightSpecs,
            expectedEvokerSpecs
        )

        expectedSpecs.zip(specs).forEach {
            assertEquals(it.first, it.second)
        }

    }

    @Test
    fun `toCharacter should create a Character with the correct properties for lol`() {
        val lolCharacterRequest = basicLolEntityEnrichedRequest
        val entity = lolCharacterRequest.toEntity(1L)
        assertEquals(1L, entity.id)
        assertEquals(basicLolEntity.name, entity.name)
        assertEquals(basicLolEntity.tag, entity.tag)
        assertEquals(basicLolEntity.puuid, entity.puuid)
        assertEquals(basicLolEntity.summonerId, entity.summonerId)
        assertEquals(basicLolEntity.summonerIcon, entity.summonerIcon)
        assertEquals(basicLolEntity.summonerLevel, entity.summonerLevel)
    }

    @Test
    fun `same should return true for identical lol entities`() {
        val lolCharacterRequest = basicLolEntityEnrichedRequest
        val entity = lolCharacterRequest.toEntity(1L)
        val result = lolCharacterRequest.same(entity)
        assertTrue(result)
    }

    @Test
    fun `same should return true for lol entities that share same puuid or summonerId regardless of other fields`() {
        val lolCharacterRequest = basicLolEntityEnrichedRequest
        val entity = lolCharacterRequest.toEntity(1L).copy(name = "diff name", tag = "diff tag")
        val result = lolCharacterRequest.same(entity)
        assertTrue(result)
    }

    @Test
    fun `same should return true for identical wow entities`() {
        val wowCharacterRequest = WowEntityRequest("Aragorn", "Middle Earth", "Gondor")
        val entity = wowCharacterRequest.toEntity(2L)
        val result = wowCharacterRequest.same(entity)
        assertTrue(result)
    }

    @Test
    fun `same should return false for wow entities with different properties`() {
        val wowCharacterRequest = WowEntityRequest("Legolas", "Middle Earth", "Lothlorien")
        val entity = wowCharacterRequest.toEntity(3L)
        val result = wowCharacterRequest.same(entity.copy(name = "DifferentName"))
        assertFalse(result)
    }

    @Test
    fun `same should return false for lol entities with different properties`() {
        val lolCharacterRequest = basicLolEntityEnrichedRequest
        val entity = lolCharacterRequest.toEntity(1L)
        val diffPuuid = lolCharacterRequest.same(entity.copy(puuid = "diff-puuid"))
        val diffSummonerId = lolCharacterRequest.same(entity.copy(summonerId = "diff-summonerId"))
        assertFalse(diffPuuid)
        assertFalse(diffSummonerId)
    }

    @Test
    fun `same should return true for wow entities with identical Blizzard IDs`() {
        val enrichedRequest = createWowCharacterEnrichedRequest(
            name = "Arthas",
            region = "Northrend",
            realm = "Icecrown",
            blizzardId = 12345L
        )
        val entity = enrichedRequest.toEntity(1L)
        val result = enrichedRequest.same(entity)
        assertTrue(result)
    }

    @Test
    fun `same should return false for wow entities with different Blizzard IDs`() {
        val enrichedRequest = createWowCharacterEnrichedRequest(
            name = "Thrall",
            region = "Azeroth",
            realm = "Orgrimmar",
            blizzardId = 67890L
        )
        val entity = enrichedRequest.toEntity(2L)
        val result = enrichedRequest.same(entity.copy(blizzardId = 99999L))
        assertFalse(result)
    }

    @Test
    fun `toCharacter should create a WowCharacter with the correct properties for WowCharacterEnrichedRequest`() {
        val entityName = "Sylvanas"
        val entityRegion = "Eastern Kingdoms"
        val entityRealm = "Silvermoon"

        val enrichedRequest = createWowCharacterEnrichedRequest(
            name = "Sylvanas",
            region = "Eastern Kingdoms",
            realm = "Silvermoon",
            blizzardId = 54321L
        )
        val entity = enrichedRequest.toEntity(1L)

        assertEquals(1L, entity.id)
        assertEquals(entityName, entity.name)
        assertEquals(entityRegion, entity.region)
        assertEquals(entityRealm, entity.realm)
        assertEquals(54321L, entity.blizzardId)
    }

    private fun createWowCharacterEnrichedRequest(
        name: String = "DefaultName",
        region: String = "DefaultRegion",
        realm: String = "DefaultRealm",
        blizzardId: Long? = 12345L
    ) = WowEnrichedEntityRequest(name, region, realm, blizzardId)
}
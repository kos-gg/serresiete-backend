package com.kos.characters

import com.kos.characters.CharactersTestHelper.basicLolCharacter
import com.kos.characters.CharactersTestHelper.basicLolCharacterEnrichedRequest
import com.kos.characters.CharactersTestHelper.basicWowCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharactersDomainTest {

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
        val lolCharacterRequest = basicLolCharacterEnrichedRequest
        val character = lolCharacterRequest.toCharacter(1L)
        assertEquals(1L, character.id)
        assertEquals(basicLolCharacter.name, character.name)
        assertEquals(basicLolCharacter.tag, character.tag)
        assertEquals(basicLolCharacter.puuid, character.puuid)
        assertEquals(basicLolCharacter.summonerId, character.summonerId)
        assertEquals(basicLolCharacter.summonerIcon, character.summonerIcon)
        assertEquals(basicLolCharacter.summonerLevel, character.summonerLevel)
    }

    @Test
    fun `same should return true for identical lol characters`() {
        val lolCharacterRequest = basicLolCharacterEnrichedRequest
        val character = lolCharacterRequest.toCharacter(1L)
        val result = lolCharacterRequest.same(character)
        assertTrue(result)
    }

    @Test
    fun `same should return true for lol characters that share same puuid or summonerId regardless of other fields`() {
        val lolCharacterRequest = basicLolCharacterEnrichedRequest
        val character = lolCharacterRequest.toCharacter(1L).copy(name = "diff name", tag = "diff tag")
        val result = lolCharacterRequest.same(character)
        assertTrue(result)
    }

    @Test
    fun `same should return true for identical wow characters`() {
        val wowCharacterRequest = WowCharacterRequest("Aragorn", "Middle Earth", "Gondor")
        val character = wowCharacterRequest.toCharacter(2L)
        val result = wowCharacterRequest.same(character)
        assertTrue(result)
    }

    @Test
    fun `same should return false for wow characters with different properties`() {
        val wowCharacterRequest = WowCharacterRequest("Legolas", "Middle Earth", "Lothlorien")
        val character = wowCharacterRequest.toCharacter(3L)
        val result = wowCharacterRequest.same(character.copy(name = "DifferentName"))
        assertFalse(result)
    }

    @Test
    fun `same should return false for lol characters with different properties`() {
        val lolCharacterRequest = basicLolCharacterEnrichedRequest
        val character = lolCharacterRequest.toCharacter(1L)
        val diffPuuid = lolCharacterRequest.same(character.copy(puuid = "diff-puuid"))
        val diffSummonerId = lolCharacterRequest.same(character.copy(summonerId = "diff-summonerId"))
        assertFalse(diffPuuid)
        assertFalse(diffSummonerId)
    }

    @Test
    fun `same should return true for wow characters with identical Blizzard IDs`() {
        val enrichedRequest = createWowCharacterEnrichedRequest(
            name = "Arthas",
            region = "Northrend",
            realm = "Icecrown",
            blizzardId = 12345L
        )
        val character = enrichedRequest.toCharacter(1L)
        val result = enrichedRequest.same(character)
        assertTrue(result)
    }

    @Test
    fun `same should return false for wow characters with different Blizzard IDs`() {
        val enrichedRequest = createWowCharacterEnrichedRequest(
            name = "Thrall",
            region = "Azeroth",
            realm = "Orgrimmar",
            blizzardId = 67890L
        )
        val character = enrichedRequest.toCharacter(2L)
        val result = enrichedRequest.same(character.copy(blizzardId = 99999L))
        assertFalse(result)
    }

    @Test
    fun `toCharacter should create a WowCharacter with the correct properties for WowCharacterEnrichedRequest`() {
        val characterName = "Sylvanas"
        val characterRegion = "Eastern Kingdoms"
        val characterRealm = "Silvermoon"

        val enrichedRequest = createWowCharacterEnrichedRequest(
            name = "Sylvanas",
            region = "Eastern Kingdoms",
            realm = "Silvermoon",
            blizzardId = 54321L
        )
        val character = enrichedRequest.toCharacter(1L)

        assertEquals(1L, character.id)
        assertEquals(characterName, character.name)
        assertEquals(characterRegion, character.region)
        assertEquals(characterRealm, character.realm)
        assertEquals(54321L, character.blizzardId)
    }

    private fun createWowCharacterEnrichedRequest(
        name: String = "DefaultName",
        region: String = "DefaultRegion",
        realm: String = "DefaultRealm",
        blizzardId: Long? = 12345L
    ) = WowCharacterEnrichedRequest(name, region, realm, blizzardId)
}
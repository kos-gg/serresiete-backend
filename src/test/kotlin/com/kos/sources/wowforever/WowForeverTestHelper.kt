package com.kos.sources.wowforever

import com.kos.datacache.domain.WowForeverData
import com.kos.datacache.domain.WowForeverSpecialization
import com.kos.datacache.domain.WowForeverTalent
import com.kos.datacache.domain.WowPrice
import com.kos.entities.domain.WowForeverCharacter
import com.kos.sources.wowhc.WowEquippedItemRef
import com.kos.sources.wowhc.WowItemDetails
import java.time.OffsetDateTime
import java.time.ZoneOffset

object WowForeverTestHelper {
    private val lastLogin = OffsetDateTime.of(2026, 11, 5, 20, 0, 0, 0, ZoneOffset.UTC)

    val foreverCharacter = WowForeverCharacter(
        id = 6880137,
        name = "Sanxei",
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
        lastLogin = lastLogin
    )

    val foreverData = WowForeverData(
        id = 6880137,
        name = "Sanxei",
        surname = "Tarde",
        region = "eu",
        level = 14,
        averageItemLevel = 8,
        equippedItemLevel = 8,
        characterClass = "Warrior",
        race = "Orc",
        gender = "Female",
        faction = "Horde",
        guild = "TSALAB",
        experience = 11355,
        ruleset = "PvP",
        lastLogin = lastLogin
    )

    private fun equippedItem(id: Long, slot: String, name: String) = WowForeverEquippedItem(
        WowEquippedItemRef(id, slot, listOf()),
        WowItemDetails(
            id = id,
            quality = "Uncommon",
            name = name,
            level = 18,
            binding = "Binds when equipped",
            requiredLevel = 13,
            itemSubclass = "Mail",
            armor = "100 Armor",
            stats = listOf("+2 Strength"),
            spells = listOf(),
            sellPrice = WowPrice("Sell Price:", "0", "2", "95"),
            durability = null,
            weaponStats = null
        )
    )

    val boots = equippedItem(6350, "Feet", "Rough Bronze Boots")
    val belt = equippedItem(2857, "Waist", "Runed Copper Belt")

    const val bootsIcon = "https://render.worldofwarcraft.com/forever-eu/icons/56/boots.jpg"

    val heroicStrike = WowForeverTalent(
        name = "Improved Heroic Strike",
        spellId = 12282,
        rank = 2,
        description = "Reduces the cost of your Heroic Strike ability by 2 Rage.",
        icon = null
    )
    val rend = WowForeverTalent(
        name = "Improved Rend",
        spellId = 12286,
        rank = 3,
        description = "Increases the Bleed damage done by your Rend ability by 35%.",
        icon = null
    )

    fun arms(vararg talents: WowForeverTalent) = WowForeverSpecialization("Arms", talents.sumOf { it.rank }, talents.toList())

    const val heroicStrikeIcon = "https://render.worldofwarcraft.com/forever-eu/icons/56/132282.jpg"
}

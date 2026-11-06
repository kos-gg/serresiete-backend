package com.kos.sources.wowforever

import com.kos.datacache.domain.WowForeverData
import com.kos.entities.domain.WowForeverCharacter
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
}

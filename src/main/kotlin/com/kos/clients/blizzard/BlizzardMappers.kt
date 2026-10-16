package com.kos.clients.blizzard

import com.kos.clients.domain.*
import com.kos.datacache.domain.*
import com.kos.entities.domain.GuildRoster
import com.kos.entities.domain.GuildRosterMember
import com.kos.entities.domain.WowRealm
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

fun GetWowCharacterResponse.toWowCharacter() = WowCharacter(
    id = id,
    name = name,
    level = level,
    isDead = isDead ?: false,
    isSelfFound = isSelfFound ?: false,
    averageItemLevel = averageItemLevel,
    equippedItemLevel = equippedItemLevel,
    characterClass = characterClass,
    faction = faction,
    race = race,
    gender = gender,
    realmId = realm.id,
    realm = realm.name,
    guild = guild,
    experience = experience,
    lastLogin = OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastLogin), ZoneId.systemDefault())
)

fun GetWowMediaResponse.asset(key: String): String? = assets.find { it.key == key }?.value

fun GetWowEquipmentResponse.toEquippedItemRefs() =
    equippedItems.map { WowEquippedItemRef(it.item.id, it.slot.name, it.enchantments) }

fun GetWowSpecializationsResponse.toActiveSpecializations() =
    specializationGroups.firstOrNull { it.isActive }?.specializations?.map { specialization ->
        WowSpecialization(
            specialization.specializationName,
            specialization.spentPoints,
            specialization.talents.map { WowTalent(it.talent.id, it.talentRank) }
        )
    }.orEmpty()

fun GetWowCharacterStatsResponse.toWowStats() = WowStats(
    health,
    WowResource(powerType, power),
    strength,
    agility,
    intellect,
    stamina,
    meleeCrit,
    attackPower,
    WowWeaponStats(mainHandDamageMin, mainHandDamageMax, mainHandSpeed, mainHandDps),
    WowWeaponStats(offHandDamageMin, offHandDamageMax, offHandSpeed, offHandDps),
    spellPower,
    spellPenetration,
    spellCrit,
    manaRegen,
    manaRegenCombat,
    armor,
    dodge,
    parry,
    block,
    rangedCrit,
    spirit,
    defense,
    WowResistances(fireResistance, holyResistance, shadowResistance, natureResistance, arcaneResistance)
)

fun GetWowItemResponse.toWowItemDetails() = WowItemDetails(
    id,
    previewItem.quality,
    name,
    level,
    previewItem.binding,
    requiredLevel,
    previewItem.itemSubclass,
    previewItem.armor,
    previewItem.stats,
    previewItem.spells,
    previewItem.sellPrice?.let { WowPrice(it.header, it.gold, it.silver, it.copper) },
    previewItem.durability,
    previewItem.weapon?.let { WowWeaponDisplayableStats(it.damage, it.dps, it.attackSpeed) }
)

fun GetWowRealmResponse.toWowRealm() = WowRealm(category)

fun GetWowRosterResponse.toGuildRoster() = GuildRoster(
    guild.id,
    members.map { GuildRosterMember(it.character.name, it.character.level, it.character.id, it.character.realm?.slug) }
)

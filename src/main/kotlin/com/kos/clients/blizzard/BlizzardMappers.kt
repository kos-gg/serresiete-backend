package com.kos.clients.blizzard

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.kos.clients.JsonParseError
import com.kos.clients.domain.*
import com.kos.datacache.domain.*
import com.kos.entities.domain.GuildRoster
import com.kos.entities.domain.GuildRosterMember
import com.kos.entities.domain.WowRealm
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import com.kos.sources.wowhc.WowEquippedItemRef
import com.kos.sources.wowhc.WowItemDetails
import com.kos.entities.domain.WowCharacter
import com.kos.entities.domain.WowForeverCharacter
import com.kos.entities.domain.findClassAndSpec
import com.kos.sources.wow.KeystoneBestRun
import com.kos.sources.wow.MythicKeystoneProfile
import com.kos.sources.wowforever.WowForeverEquippedItem

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

fun GetWowForeverCharacterResponse.toWowForeverCharacter() = WowForeverCharacter(
    id = id,
    name = name,
    surname = surname,
    level = level,
    averageItemLevel = averageItemLevel,
    equippedItemLevel = equippedItemLevel,
    characterClass = characterClass,
    faction = faction,
    race = race,
    gender = gender,
    guild = guild,
    experience = experience,
    ruleset = ruleset,
    lastLogin = OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastLogin), ZoneId.systemDefault())
)

fun GetWowMediaResponse.asset(key: String): String? = assets.find { it.key == key }?.value

fun GetWowEquipmentResponse.toEquippedItemRefs() =
    equippedItems.map { WowEquippedItemRef(it.item.id, it.slot.name, it.enchantments) }

fun GetWowForeverEquipmentResponse.toWowForeverEquippedItems() =
    equippedItems.map { equipped ->
        WowForeverEquippedItem(
            WowEquippedItemRef(equipped.item.id, equipped.slot.name, equipped.enchantments),
            WowItemDetails(
                equipped.item.id,
                equipped.quality,
                equipped.name,
                equipped.level.value,
                equipped.binding,
                equipped.requirements?.level?.value ?: 0,
                equipped.itemSubclass,
                equipped.armor,
                equipped.stats,
                equipped.spells,
                equipped.sellPrice?.let { WowPrice(it.header, it.gold, it.silver, it.copper) },
                equipped.durability,
                equipped.weapon?.let { WowWeaponDisplayableStats(it.damage, it.dps, it.attackSpeed) }
            )
        )
    }

fun GetWowForeverSpecializationsResponse.toActiveForeverSpecializations() =
    specializationGroups.firstOrNull { it.isActive }?.specializations.orEmpty().map { specialization ->
        WowForeverSpecialization(
            specialization.specializationName,
            specialization.spentPoints,
            specialization.talents.map {
                WowForeverTalent(
                    name = it.talent,
                    spellId = it.spellTooltip.spell.id,
                    rank = it.talentRank,
                    description = it.spellTooltip.description,
                    icon = null
                )
            }
        )
    }

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

private val hardcoreRealmCategories = setOf("Hardcore", "Anniversary")

fun GetWowRealmResponse.toWowRealm() = WowRealm(category in hardcoreRealmCategories)

fun GetWowRosterResponse.toGuildRoster() = GuildRoster(
    guild.id,
    members.map { GuildRosterMember(it.character.name, it.character.level, it.character.id, it.character.realm?.slug) }
)

fun GetWowMythicKeystoneSeasonResponse.toMythicKeystoneProfile(): Either<JsonParseError, MythicKeystoneProfile> =
    either {
        MythicKeystoneProfile(
            rating = mythicRating?.rating ?: 0.0,
            bestRuns = bestRuns.map { run ->
                KeystoneBestRun(
                    dungeonId = run.dungeon.id,
                    keyLevel = run.keystoneLevel,
                    timed = run.isCompletedWithinTime,
                    details = RunDetails(run.members.map { it.toRunMember().bind() })
                )
            }
        )
    }

private fun KeystoneRunMemberResponse.toRunMember(): Either<JsonParseError, RunMember> =
    either {
        val (wowClass, spec) = ensureNotNull(findClassAndSpec(specialization.id)) {
            JsonParseError("/best_runs/members/specialization/id unknown: ${specialization.id}")
        }
        RunMember(
            name = character.name,
            realm = character.realm.slug,
            `class` = wowClass,
            spec = ClassSpec(spec.externalSpec, specialization.name, spec.role),
            race = race.name,
            itemLevel = equippedItemLevel
        )
    }

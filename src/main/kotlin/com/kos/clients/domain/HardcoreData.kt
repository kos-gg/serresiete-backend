package com.kos.clients.domain

import com.kos.clients.domain.blizzard.*
import com.kos.clients.domain.raiderio.RaiderioWowHeadEmbeddedResponse
import com.kos.common.OffsetDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

@Serializable
data class WowPrice(val header: String, val gold: String, val silver: String, val copper: String) {
    companion object {
        fun apply(priceResponse: WowPriceResponse): WowPrice = WowPrice(
            priceResponse.header,
            priceResponse.gold,
            priceResponse.silver,
            priceResponse.copper
        )
    }
}

@Serializable
data class WowWeaponDisplayableStats(
    val damage: String,
    val dps: String,
    val attackSpeed: String
) {
    companion object {
        fun apply(response: WowWeaponStatsResponse) =
            WowWeaponDisplayableStats(
                response.damage,
                response.dps,
                response.attackSpeed
            )
    }
}

@Serializable
data class WowItem(
    val id: Long,
    val slot: String,
    val quality: String,
    val name: String,
    val level: Int,
    val binding: String?,
    val requiredLevel: Int,
    val itemSubclass: String,
    val armor: String?,
    val stats: List<String>,
    val spells: List<String>,
    val sellPrice: WowPrice?,
    val durability: String?,
    val weaponStats: WowWeaponDisplayableStats?,
    val icon: String?,
    val enchantments: List<String>
)

@Serializable
data class WowResource(val type: String, val value: Int)

@Serializable
data class WowWeaponStats(
    val minDamage: Double,
    val maxDamage: Double,
    val speed: Double,
    val dps: Double
)

@Serializable
data class WowResistances(
    val fire: Int,
    val holy: Int,
    val shadow: Int,
    val nature: Int,
    val arcane: Int
)

@Serializable
data class WowStats(
    val health: Int,
    val resource: WowResource,
    val strength: Int,
    val agility: Int,
    val intellect: Int,
    val stamina: Int,
    val meleeCrit: Double,
    val attackPower: Int,
    val mainHandStats: WowWeaponStats,
    val offHandStats: WowWeaponStats,
    val spellPower: Double,
    val spellPenetration: Double,
    val spellCrit: Double,
    val manaRegen: Double,
    val manaRegenCombat: Double,
    val armor: Int,
    val dodge: Double,
    val parry: Double,
    val block: Double,
    val rangedCrit: Double,
    val spirit: Int,
    val defense: Int,
    val resistances: WowResistances
) {
    companion object {
        fun apply(response: GetWowCharacterStatsResponse): WowStats =
            WowStats(
                response.health,
                WowResource(response.powerType, response.power),
                response.strength,
                response.agility,
                response.intellect,
                response.stamina,
                response.meleeCrit,
                response.attackPower,
                WowWeaponStats(
                    response.mainHandDamageMin,
                    response.mainHandDamageMax,
                    response.mainHandSpeed,
                    response.mainHandDps
                ),
                WowWeaponStats(
                    response.offHandDamageMin,
                    response.offHandDamageMax,
                    response.offHandSpeed,
                    response.offHandDps
                ),
                response.spellPower,
                response.spellPenetration,
                response.spellCrit,
                response.manaRegen,
                response.manaRegenCombat,
                response.armor,
                response.dodge,
                response.parry,
                response.block,
                response.rangedCrit,
                response.spirit,
                response.defense,
                WowResistances(
                    response.fireResistance,
                    response.holyResistance,
                    response.shadowResistance,
                    response.natureResistance,
                    response.arcaneResistance
                )
            )
    }
}

@Serializable
data class WowTalent(
    val id: Int,
    val rank: Int
)

@Serializable
data class WowSpecialization(
    val name: String,
    val points: Int,
    val talents: List<WowTalent>
) {
    companion object {
        fun apply(specialization: Specialization): WowSpecialization =
            WowSpecialization(
                specialization.specializationName,
                specialization.spentPoints,
                specialization.talents.map {
                    WowTalent(
                        it.talent.id,
                        it.talentRank
                    )
                }
            )
    }
}

@Serializable
data class WowTalents(
    val wowHeadEmbeddedTalents: String?,
    val specializations: List<WowSpecialization>
)

@Serializable
data class HardcoreData(
    val id: Long,
    val name: String,
    val level: Int,
    val isDead: Boolean,
    val isSelfFound: Boolean,
    val averageItemLevel: Int,
    val equippedItemLevel: Int,
    val characterClass: String,
    val race: String,
    val gender: String,
    val realm: String,
    val region: String,
    val guild: String?,
    val experience: Int,
    val items: List<WowItem>,
    val faction: String,
    val avatar: String?,
    val stats: WowStats,
    val specializations: WowTalents,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val lastLogin: OffsetDateTime
) : Data {
    companion object {
        fun apply(
            region: String,
            characterResponse: GetWowCharacterResponse,
            mediaResponse: GetWowMediaResponse,
            alreadyExistentItems: List<WowItem>,
            //TODO: Refactor this triple into an apply
            equipmentResponse: List<Triple<WowEquippedItemResponse, GetWowItemResponse, GetWowMediaResponse?>>,
            statsResponse: GetWowCharacterStatsResponse,
            specializationsResponse: GetWowSpecializationsResponse,
            wowHeadEmbeddedResponse: RaiderioWowHeadEmbeddedResponse?
        ) = HardcoreData(
            characterResponse.id,
            characterResponse.name,
            characterResponse.level,
            characterResponse.isDead ?: false,
            characterResponse.isSelfFound ?: false,
            characterResponse.averageItemLevel,
            characterResponse.equippedItemLevel,
            characterResponse.characterClass,
            characterResponse.race,
            characterResponse.gender,
            characterResponse.realm.name,
            region,
            characterResponse.guild,
            characterResponse.experience,
            alreadyExistentItems + equipmentResponse.map { (equipped, item, icon) ->
                WowItem(
                    item.id,
                    equipped.slot.name,
                    item.previewItem.quality,
                    item.name,
                    item.level,
                    item.previewItem.binding,
                    item.requiredLevel,
                    item.previewItem.itemSubclass,
                    item.previewItem.armor,
                    item.previewItem.stats,
                    item.previewItem.spells,
                    item.previewItem.sellPrice?.let { WowPrice.apply(it) },
                    item.previewItem.durability,
                    item.previewItem.weapon?.let { WowWeaponDisplayableStats.apply(it) },
                    icon?.assets?.find { it.key == "icon" }?.value,
                    equipped.enchantments
                )
            },
            characterResponse.faction,
            mediaResponse.assets.find { it.key == "avatar" }?.value,
            WowStats.apply(statsResponse),
            WowTalents(
                wowHeadEmbeddedTalents = wowHeadEmbeddedResponse?.talentLoadout?.wowheadCalculator,
                specializations = specializationsResponse.specializationGroups.firstOrNull { it.isActive }?.specializations?.map { specialization ->
                    WowSpecialization.apply(specialization)
                }.orEmpty()
            ),
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(characterResponse.lastLogin), ZoneId.systemDefault())
        )
    }
}

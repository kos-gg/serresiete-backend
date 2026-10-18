package com.kos.datacache.domain

import com.kos.common.OffsetDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
data class WowPrice(val header: String, val gold: String, val silver: String, val copper: String)

@Serializable
data class WowWeaponDisplayableStats(
    val damage: String,
    val dps: String,
    val attackSpeed: String
)

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
)

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
)

@Serializable
data class WowTalents(
    val wowHeadEmbeddedTalents: String?,
    val specializations: List<WowSpecialization>
)

@Serializable
@SerialName("wow_hc")
data class WowHardcoreData(
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
) : Data

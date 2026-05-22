package com.kos.clients.domain.blizzard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Realm(val name: String, val id: Long)

@Serializable
data class AssetKeyValue(val key: String, val value: String)

@Serializable
data class GetWowMediaResponse(
    val assets: List<AssetKeyValue>
)

@Serializable
data class GetWowRealmResponse(val category: String)

@Serializable
data class GetWowCharacterResponse(
    val id: Long,
    val name: String,
    val level: Int,
    @SerialName("is_ghost")
    val isDead: Boolean? = null,
    @SerialName("is_self_found")
    val isSelfFound: Boolean? = null,
    @SerialName("average_item_level")
    val averageItemLevel: Int,
    @SerialName("equipped_item_level")
    val equippedItemLevel: Int,
    @Serializable(with = NameExtractorSerializer::class)
    @SerialName("character_class")
    val characterClass: String,
    @Serializable(with = NameExtractorSerializer::class)
    val faction: String,
    @Serializable(with = NameExtractorSerializer::class)
    val race: String,
    @Serializable(with = NameExtractorSerializer::class)
    val gender: String,
    val realm: Realm,
    @Serializable(with = NameExtractorSerializer::class)
    val guild: String? = null,
    val experience: Int,
    @SerialName("last_login_timestamp")
    val lastLogin: Long
)

@Serializable
data class GetWowCharacterStatsResponse(
    val health: Int,
    val power: Int,
    @SerialName("power_type")
    @Serializable(with = NameExtractorSerializer::class)
    val powerType: String,
    @Serializable(with = EffectiveExtractorSerializer::class)
    val strength: Int,
    @Serializable(with = EffectiveExtractorSerializer::class)
    val agility: Int,
    @Serializable(with = EffectiveExtractorSerializer::class)
    val intellect: Int,
    @Serializable(with = EffectiveExtractorSerializer::class)
    val stamina: Int,
    @Serializable(with = ValueExtractorSerializer::class)
    @SerialName("melee_crit")
    val meleeCrit: Double,
    @SerialName("attack_power")
    val attackPower: Int,
    @SerialName("main_hand_damage_min")
    val mainHandDamageMin: Double,
    @SerialName("main_hand_damage_max")
    val mainHandDamageMax: Double,
    @SerialName("main_hand_speed")
    val mainHandSpeed: Double,
    @SerialName("main_hand_dps")
    val mainHandDps: Double,
    @SerialName("off_hand_damage_min")
    val offHandDamageMin: Double,
    @SerialName("off_hand_damage_max")
    val offHandDamageMax: Double,
    @SerialName("off_hand_speed")
    val offHandSpeed: Double,
    @SerialName("off_hand_dps")
    val offHandDps: Double,
    @SerialName("spell_power")
    val spellPower: Double,
    @SerialName("spell_penetration")
    val spellPenetration: Double,
    @Serializable(with = ValueExtractorSerializer::class)
    @SerialName("spell_crit")
    val spellCrit: Double,
    @SerialName("mana_regen")
    val manaRegen: Double,
    @SerialName("mana_regen_combat")
    val manaRegenCombat: Double,
    @Serializable(with = EffectiveExtractorSerializer::class)
    val armor: Int,
    @Serializable(with = ValueExtractorSerializer::class)
    val dodge: Double,
    @Serializable(with = ValueExtractorSerializer::class)
    val parry: Double,
    @Serializable(with = ValueExtractorSerializer::class)
    val block: Double,
    @SerialName("ranged_crit")
    @Serializable(with = ValueExtractorSerializer::class)
    val rangedCrit: Double,
    @Serializable(with = EffectiveExtractorSerializer::class)
    val spirit: Int,
    @Serializable(with = EffectiveExtractorSerializer::class)
    val defense: Int,
    @SerialName("fire_resistance")
    @Serializable(with = EffectiveExtractorSerializer::class)
    val fireResistance: Int,
    @SerialName("holy_resistance")
    @Serializable(with = EffectiveExtractorSerializer::class)
    val holyResistance: Int,
    @SerialName("shadow_resistance")
    @Serializable(with = EffectiveExtractorSerializer::class)
    val shadowResistance: Int,
    @Serializable(with = EffectiveExtractorSerializer::class)
    @SerialName("nature_resistance")
    val natureResistance: Int,
    @Serializable(with = EffectiveExtractorSerializer::class)
    @SerialName("arcane_resistance")
    val arcaneResistance: Int
)

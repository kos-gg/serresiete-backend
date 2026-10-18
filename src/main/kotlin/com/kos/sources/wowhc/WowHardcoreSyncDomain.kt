package com.kos.sources.wowhc

import com.kos.datacache.domain.WowHardcoreData
import com.kos.datacache.domain.WowItem
import com.kos.datacache.domain.WowPrice
import com.kos.datacache.domain.WowStats
import com.kos.datacache.domain.WowTalents
import com.kos.datacache.domain.WowWeaponDisplayableStats
import com.kos.entities.domain.WowCharacter

data class WowEquippedItemRef(
    val itemId: Long,
    val slot: String,
    val enchantments: List<String>
)

data class WowItemDetails(
    val id: Long,
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
    val weaponStats: WowWeaponDisplayableStats?
) {
    fun toWowItem(equipped: WowEquippedItemRef, icon: String?) = WowItem(
        id,
        equipped.slot,
        quality,
        name,
        level,
        binding,
        requiredLevel,
        itemSubclass,
        armor,
        stats,
        spells,
        sellPrice,
        durability,
        weaponStats,
        icon,
        equipped.enchantments
    )
}

fun WowCharacter.toWowHardcoreData(
    region: String,
    avatar: String?,
    items: List<WowItem>,
    stats: WowStats,
    talents: WowTalents
) = WowHardcoreData(
    id,
    name,
    level,
    isDead,
    isSelfFound,
    averageItemLevel,
    equippedItemLevel,
    characterClass,
    race,
    gender,
    realm,
    region,
    guild,
    experience,
    items,
    faction,
    avatar,
    stats,
    talents,
    lastLogin
)

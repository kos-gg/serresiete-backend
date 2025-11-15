package com.kos.clients.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class StaticWowItem(
    val itemId: Long,
    val name: String,
    val icon: String? = null,
    @SerialName("class") val clazz: String? = null,
    val subclass: String? = null,
    val sellPrice: Long? = null,
    val quality: String,
    val itemLevel: Int,
    val requiredLevel: Int,
    val slot: String? = null,
    val tooltip: List<StaticTooltipEntry> = emptyList()
) {
    fun toBlizzard(): GetWowItemResponse {

        // ---- extract fields from tooltip ----
        val armor = tooltip.firstOrNull { it.label.endsWith("Armor") }?.label

        val stats = tooltip
            .filter { it.label.startsWith("+") } // +5 Agility
            .map { it.label }

        val spells = tooltip
            .filter { it.label.startsWith("Chance on hit:") || it.label.startsWith("Equip:") }
            .map { it.label }

        val binding = tooltip
            .firstOrNull { it.label.contains("Binds") }
            ?.label

        val durability = tooltip
            .firstOrNull { it.label.startsWith("Durability") }
            ?.label

        // ---- weapon specific fields ----
        val damage = tooltip.firstOrNull { it.label.contains("Damage") && !it.label.contains("per second") }?.label
        val dps = tooltip.firstOrNull { it.label.contains("per second") }?.label
        val speed = tooltip.firstOrNull { it.label.startsWith("Speed") }?.label

        val weapon = if (damage != null || dps != null || speed != null) {
            WowWeaponStatsResponse(
                damage = damage ?: "",
                dps = dps ?: "",
                attackSpeed = speed ?: ""
            )
        } else null

        // ---- sell price ----
        val gold = sellPrice?.div(10000)
        val silver = (sellPrice?.rem(10000))?.div(100)
        val copper = sellPrice?.rem(100)

        val blizzSellPrice = WowPriceResponse(
            header = "Sell Price:",
            gold = gold.toString(),
            silver = silver.toString(),
            copper = copper.toString()
        )

        // ---- preview item ----
        val preview = WowPreviewItem(
            quality = quality,
            itemSubclass = subclass!!,
            slot = slot!!,
            armor = armor,
            stats = stats,
            spells = spells,
            sellPrice = blizzSellPrice,
            durability = durability,
            binding = binding,
            weapon = weapon
        )

        // ---- final result ----
        return GetWowItemResponse(
            id = itemId,
            name = name,
            level = itemLevel,
            requiredLevel = requiredLevel,
            previewItem = preview
        )
    }

    fun toBlizzardMedia(): GetWowMediaResponse {
        val iconUrl = icon?.let {
            "https://render-classic-us.worldofwarcraft.com/icons/56/$it.jpg"
        } ?: "https://render-classic-us.worldofwarcraft.com/icons/56/inv_misc_questionmark.jpg"

        return GetWowMediaResponse(
            assets = listOf(
                AssetKeyValue(key = "icon", value = iconUrl)
            )
        )
    }
}

@Serializable
data class StaticTooltipEntry(
    val label: String,
    val format: String? = null
)
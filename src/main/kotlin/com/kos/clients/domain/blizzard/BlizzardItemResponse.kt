package com.kos.clients.domain.blizzard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = WowPriceSerializer::class)
data class WowPriceResponse(val header: String, val gold: String, val silver: String, val copper: String)

@Serializable
data class WowWeaponStatsResponse(
    @Serializable(with = DisplayableStringExtractorSerializer::class)
    val damage: String,
    @Serializable(with = DisplayableStringExtractorSerializer::class)
    val dps: String,
    @SerialName("attack_speed")
    @Serializable(with = DisplayableStringExtractorSerializer::class)
    val attackSpeed: String
)

@Serializable
data class WowPreviewItem(
    @Serializable(with = NameExtractorSerializer::class)
    val quality: String,
    @SerialName("item_subclass")
    @Serializable(with = NameExtractorSerializer::class)
    val itemSubclass: String,
    @SerialName("inventory_type")
    @Serializable(with = NameExtractorSerializer::class)
    val slot: String,
    @Serializable(with = NameExtractorSerializer::class)
    val binding: String? = null,
    @Serializable(with = NestedDisplayableStringExtractorSerializer::class)
    val armor: String? = null,
    @Serializable(with = NestedDisplayableStringListSerializer::class)
    val stats: List<String> = listOf(),
    @Serializable(with = DescriptionListSerializer::class)
    val spells: List<String> = listOf(),
    @SerialName("sell_price")
    val sellPrice: WowPriceResponse? = null,
    @Serializable(with = DisplayableStringExtractorSerializer::class)
    val durability: String? = null,
    val weapon: WowWeaponStatsResponse? = null
)

@Serializable
data class GetWowItemResponse(
    val id: Long,
    val name: String,
    val level: Int,
    @SerialName("required_level")
    val requiredLevel: Int,
    @SerialName("preview_item")
    val previewItem: WowPreviewItem
)

@Serializable
data class WowItemId(val id: Long)

@Serializable
data class WowItemSlot(val name: String)

@Serializable
data class WowItemQuality(val type: String)

@Serializable
data class WowEquippedItemResponse(
    val item: WowItemId,
    val slot: WowItemSlot,
    @Serializable(with = DisplayStringListSerializer::class)
    val enchantments: List<String> = listOf(),
    val quality: WowItemQuality,
    val name: String,
)

@Serializable
data class GetWowEquipmentResponse(
    @SerialName("equipped_items")
    val equippedItems: List<WowEquippedItemResponse>
)

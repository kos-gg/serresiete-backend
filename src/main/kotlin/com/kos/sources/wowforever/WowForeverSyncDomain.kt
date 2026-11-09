package com.kos.sources.wowforever

import com.kos.sources.wowhc.WowEquippedItemRef
import com.kos.sources.wowhc.WowItemDetails

data class WowForeverEquippedItem(
    val ref: WowEquippedItemRef,
    val details: WowItemDetails
) {
    fun toWowItem(icon: String?) = details.toWowItem(ref, icon)
}

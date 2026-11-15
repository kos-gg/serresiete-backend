package com.kos.entities.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
@SerialName("wow_forever")
data class WowForeverEntityRequest(
    override val name: String,
    val surname: String = "",
    val region: String,
    val blizzardId: Long? = null,
    override val alias: String? = null
) : EntityRequest, InsertEntityRequest {
    override fun toEntity(id: Long) = WowForeverEntity(id, name, surname, region, blizzardId)
    override fun same(other: Entity): Boolean = when (other) {
        is WowForeverEntity ->
            if (this.blizzardId != null && other.blizzardId != null) this.blizzardId == other.blizzardId
            else this.name == other.name && this.surname == other.surname && this.region == other.region

        else -> false
    }

    override fun toRequest(): EntityRequest = this.copy(blizzardId = null)
    override fun toResponse(): EntityResponse = WowForeverEntityResponse(name, surname, region)
}

@Serializable
data class WowForeverEntityResponse(
    override val name: String,
    val surname: String,
    val region: String
) : EntityResponse

@Serializable
@SerialName("wow_forever")
data class WowForeverEntity(
    override val id: Long,
    override val name: String,
    val surname: String,
    val region: String,
    val blizzardId: Long?
) : Entity {
    override fun toRequest(): WowForeverEntityRequest = WowForeverEntityRequest(name, surname, region)
}

data class WowForeverCharacter(
    val id: Long,
    val name: String,
    val surname: String,
    val level: Int,
    val averageItemLevel: Int,
    val equippedItemLevel: Int,
    val characterClass: String,
    val faction: String,
    val race: String,
    val gender: String,
    val guild: String?,
    val experience: Int,
    val ruleset: String,
    val lastLogin: OffsetDateTime
)

data class WowForeverGuildRoster(
    val guildId: Long,
    val members: List<WowForeverGuildMember>
)

data class WowForeverGuildMember(
    val id: Long,
    val name: String,
    val surname: String
)

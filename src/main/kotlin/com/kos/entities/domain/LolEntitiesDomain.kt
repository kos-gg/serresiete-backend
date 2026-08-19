package com.kos.entities.domain

import kotlinx.serialization.Serializable

@Serializable
data class LolEntity(
    override val id: Long,
    override val name: String,
    val tag: String,
    val puuid: String,
    val summonerIcon: Int,
    val summonerLevel: Int
) : Entity {
    override fun toRequest(): LolEntityRequest = LolEntityRequest(this.name, this.tag, null)
}

@Serializable
data class LolEntityRequest(
    override val name: String,
    val tag: String,
    override val alias: String? = null
) : EntityRequest {

    override fun same(other: Entity): Boolean {
        return when (other) {
            is LolEntity -> this.name == other.name && this.tag == other.tag
            else -> false
        }
    }

    override fun toResponse(): EntityResponse = LolEntityResponse(name, tag)
}

@Serializable
data class LolEntityResponse(
    override val name: String,
    val tag: String
) : EntityResponse

data class LolEnrichedEntityRequest(
    override val name: String,
    val tag: String,
    val puuid: String,
    val summonerIconId: Int,
    val summonerLevel: Int

) : InsertEntityRequest {
    override fun toEntity(id: Long): LolEntity {
        return LolEntity(
            id,
            this.name,
            this.tag,
            this.puuid,
            this.summonerIconId,
            this.summonerLevel
        )
    }

    override fun same(other: Entity): Boolean {
        return when (other) {
            is LolEntity -> this.puuid == other.puuid
            else -> false
        }
    }

    override fun toRequest(): EntityRequest = LolEntityRequest(this.name, this.tag, null)
}
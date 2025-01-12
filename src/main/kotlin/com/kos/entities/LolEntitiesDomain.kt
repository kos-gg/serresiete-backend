package com.kos.entities

import kotlinx.serialization.Serializable

@Serializable
data class LolEntity(
    override val id: Long,
    override val name: String,
    val tag: String,
    val puuid: String,
    val summonerIcon: Int,
    val summonerId: String,
    val summonerLevel: Int
) : Entity

@Serializable
data class LolEntityRequest(
    override val name: String,
    val tag: String
) : CreateEntityRequest {

    override fun same(other: Entity): Boolean {
        return when (other) {
            is LolEntity -> this.name == other.name && this.tag == other.tag
            else -> false
        }
    }
}

data class LolEnrichedEntityRequest(
    override val name: String,
    val tag: String,
    val puuid: String,
    val summonerIconId: Int,
    val summonerId: String,
    val summonerLevel: Int

) : InsertEntityRequest {
    override fun toEntity(id: Long): LolEntity {
        return LolEntity(
            id,
            this.name,
            this.tag,
            this.puuid,
            this.summonerIconId,
            this.summonerId,
            this.summonerLevel
        )
    }

    override fun same(other: Entity): Boolean {
        return when (other) {
            is LolEntity -> this.puuid == other.puuid && this.summonerId == other.summonerId
            else -> false
        }
    }
}
package com.kos.entities.guilds

import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.domain.WowForeverEntityRequest
import com.kos.views.Game
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Guild {
    val name: String
    val region: String
    val blizzardId: Long
}

@Serializable
@SerialName("wow")
data class WowGuild(
    override val name: String,
    val realm: String,
    override val region: String,
    override val blizzardId: Long
) : Guild

@Serializable
@SerialName("wow_forever")
data class WowForeverGuild(
    override val name: String,
    override val region: String,
    override val blizzardId: Long
) : Guild

fun Guild.realmOrNull(): String? = when (this) {
    is WowGuild -> realm
    is WowForeverGuild -> null
}

fun Guild.toRequest(): EntityRequest = when (this) {
    is WowGuild -> WowEntityRequest(name, region, realm)
    is WowForeverGuild -> WowForeverEntityRequest(name = name, region = region)
}

@Serializable
data class GuildExistsRequest(
    val name: String,
    val region: String,
    val realm: String? = null,
    val game: Game = Game.WOW
)

fun GuildExistsRequest.normalized(): GuildExistsRequest = copy(name = name.lowercase())

@Serializable
data class GuildExistsResponse(
    val guild: Guild?
)

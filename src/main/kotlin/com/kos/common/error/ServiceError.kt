package com.kos.common.error

import com.kos.entities.domain.GuildPayload
import com.kos.entities.domain.WowEntityRequest
import com.kos.views.Game

sealed class ServiceError {
    abstract fun error(): String
}

data class SyncProcessingError(
    val type: String,
    val message: String
) : ServiceError() {
    override fun error(): String = "$type: $message"
}

data class NotCompetitiveCharacter(
    val wowEntity: WowEntityRequest
) : ServiceError() {
    override fun error(): String =
        "${wowEntity.name}-${wowEntity.realm} does not meet the minimum mythic+ score"
}

data class GuildUpdateFailed(
    val guild: GuildPayload,
    val cause: Throwable
) : ServiceError() {
    override fun error(): String =
        "Couldn't update guild ${guild.name} (${guild.region}/${guild.realm}): ${cause.stackTraceToString()}"
}

data class ResolverNotFound(
    val game: Game
) : ServiceError() {
    override fun error(): String =
        "No resolver found for game [$game]"
}

data class SynchronizerNotFound(
    val game: Game
) : ServiceError() {
    override fun error(): String =
        "No synchronizer found for game [$game]"
}

class UnableToAddNewMythicPlusSeason(
    private val reason: String
) : ServiceError() {
    override fun error(): String = reason
}

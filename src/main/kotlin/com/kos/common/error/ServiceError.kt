package com.kos.common.error

import com.kos.entities.domain.WowEntityRequest
import com.kos.eventsourcing.events.EventData
import com.kos.views.Game

sealed class ServiceError {
    abstract fun error(): String
}

data class SerializationError(
    val raw: String,
    val error: String
) : ServiceError() {
    override fun error(): String = "JSON parse error. raw: $raw; error: $error"
}

data class SyncProcessingError(
    val type: String,
    val message: String
) : ServiceError() {
    override fun error(): String = "$type: $message"
}

data class NonHardcoreCharacter(
    val wowEntity: WowEntityRequest
) : ServiceError() {
    override fun error(): String =
        "${wowEntity.realm} realm is not hardcore"
}

data class NotCompetitiveCharacter(
    val wowEntity: WowEntityRequest
) : ServiceError() {
    override fun error(): String =
        "${wowEntity.name}-${wowEntity.realm} does not meet the minimum mythic+ score"
}

data class ResolveEntityError(
    val game: Game,
    val message: String
) : ServiceError() {
    override fun error(): String =
        "Couldn't resolve $game entity with error: $message"
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

class ViewEventError(
    val operation: String,
    val payload: EventData,
    val message: String
) : ServiceError() {
    override fun error(): String =
        "Couldn't $operation view with payload [$payload] with error $message"
}

class UnableToAddNewMythicPlusSeason(
    private val reason: String
) : ServiceError() {
    override fun error(): String = reason
}

class AuthTokenError(
    private val reason: String
) : ServiceError() {
    override fun error(): String = reason
}

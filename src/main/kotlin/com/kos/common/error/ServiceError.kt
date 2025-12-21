package com.kos.common.error

import com.kos.entities.WowEntityRequest
import com.kos.eventsourcing.events.ViewToBeCreatedEvent
import com.kos.eventsourcing.events.ViewToBeEditedEvent
import com.kos.eventsourcing.events.ViewToBePatchedEvent
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

data class WowHardcoreCharacterIsDead(
    val character: String,
    val characterId: Long
) : ServiceError() {
    override fun error(): String =
        "Character with name [$character] and id [$characterId] could not be synced because it is dead"
}

data class NonHardcoreCharacter(
    val wowEntity: WowEntityRequest
) : ServiceError() {
    override fun error(): String =
        "${wowEntity.realm} realm is not hardcore"
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

class ViewCreateError(
    val view: ViewToBeCreatedEvent,
    val message: String
) : ServiceError() {
    override fun error(): String =
        "Couldn't create view with payload [$view] for game [${view.game}] with error $message"
}

class ViewEditError(
    val view: ViewToBeEditedEvent,
    val message: String
) : ServiceError() {
    override fun error(): String =
        "Couldn't edit view with payload [$view] for game [${view.game}] with error $message"
}

class ViewPatchError(
    val view: ViewToBePatchedEvent,
    val message: String
) : ServiceError() {
    override fun error(): String =
        "Couldn't patch view with payload [$view] for game [${view.game}] with error $message"
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

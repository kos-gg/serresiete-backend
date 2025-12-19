package com.kos.common.error

import com.kos.entities.WowEntityRequest
import com.kos.eventsourcing.events.ViewToBeCreatedEvent
import com.kos.eventsourcing.events.ViewToBeEditedEvent
import com.kos.eventsourcing.events.ViewToBePatchedEvent
import com.kos.views.Game

sealed class ServiceError

data class SyncProcessingError(val type: String, val message: String) : ServiceError()

data class WowHardcoreCharacterIsDead(val character: String, val characterId: Long) : ServiceError() {
    fun error(): String =
        "Character with name [$character] and id [$characterId] could not be sync because it is dead"
}

data class NonHardcoreCharacter(val wowEntity: WowEntityRequest) : ServiceError() {
    fun error(): String = "${wowEntity.realm} realm is not hardcore"
}

data class ResolveEntityError(val game: Game, val message: String) : ServiceError() {
    fun error(): String = "Couldn't resolve $game entity with error: $message"
}

data class ResolverNotFound(val game: Game) : ServiceError() {
    fun error(): String = "No resolver found for game [$game]"
}

class ViewCreateError(val view: ViewToBeCreatedEvent, val message: String) : ServiceError() {
    fun error(): String = "Couln't create view with payload [$view] for game [${view.game}] with error $message"
}

class ViewEditError(val view: ViewToBeEditedEvent, val message: String) : ServiceError() {
    fun error(): String = "Couln't edit view with payload [$view] for game [${view.game}] with error $message"
}

class ViewPatchError(val view: ViewToBePatchedEvent, val message: String) : ServiceError() {
    fun error(): String = "Couln't patch view with payload [$view] for game [${view.game}] with error $message"
}

class UnableToAddNewMythicPlusSeason(val message: String) : ServiceError() {
    fun error(): String = message
}


package com.kos.common.error

import com.kos.common._fold
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.application.*
import io.ktor.server.response.*

sealed interface ControllerError
data object CantFeatureView : ControllerError
data object NotAuthorized : ControllerError
data class NotEnoughPermissions(val user: String) : ControllerError
data class CantDeleteYourself(val user: String, val userToRemove: String) : ControllerError
data class AuthenticationError(val message: String) : ControllerError
data class ViewDataError(val message: String) : ControllerError
data class EntityError(val message: String): ControllerError
data class NotFound(val id: String) : ControllerError {
    fun error(): String = "Not found $id"
}

class BadRequest(val problem: String) : ControllerError
class InvalidQueryParameter(param: String, value: String, allowed: List<String>?) : ControllerError {
    private val baseMessage = "invalid query param[$param]: $value"
    val message: String = allowed._fold({ baseMessage }, { "$baseMessage\nallowed values: $it" })
}

class InvalidTaskType(val type: String) : IllegalArgumentException("Invalid task type: $type")
class InvalidGameType(val type: String) : IllegalArgumentException("Invalid game type: $type")

class NotPublished(val id: String) : ControllerError
data object TooMuchViews : ControllerError
data object TooMuchEntities : ControllerError
data object UserWithoutRoles : ControllerError
data object ExtraArgumentsWrongType : ControllerError
data object GuildViewMoreThanTwoEntities : ControllerError

suspend fun ApplicationCall.respondWithHandledError(error: ControllerError) {
    when (error) {
        is NotFound -> respond(NotFound, error.id)
        is NotAuthorized -> respond(Unauthorized)
        is NotEnoughPermissions -> respond(Forbidden)
        is NotPublished -> respond(BadRequest, "view not published")
        is TooMuchViews -> respond(BadRequest, "too much views")
        is ExtraArgumentsWrongType -> respond(BadRequest, "wrong extra arguments type")
        is TooMuchEntities -> respond(BadRequest, "too many entities in a view")
        is BadRequest -> respond(BadRequest, error.problem)
        is GuildViewMoreThanTwoEntities -> respond(BadRequest, "guild views must have only 1 entity")
        is InvalidQueryParameter -> respond(BadRequest, error.message)
        is AuthenticationError -> respond(Unauthorized, error.message)
        is CantDeleteYourself -> respond(BadRequest, "can't delete your credentials")
        is CantFeatureView -> respond(Forbidden, "not enough permissions to feature a view")
        is ViewDataError -> respond(InternalServerError, error.message)
        is UserWithoutRoles -> respond(Forbidden, "User does not have sufficient roles")
        is EntityError -> respond(InternalServerError, error.message)
    }
}
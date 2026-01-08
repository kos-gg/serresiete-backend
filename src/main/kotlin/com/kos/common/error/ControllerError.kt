package com.kos.common.error

import com.kos.common._fold
import com.kos.entities.domain.WowEntityRequest
import com.kos.views.Game
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.application.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

sealed interface ControllerError
data object CantFeatureView : ControllerError
data object NotAuthorized : ControllerError
data class NotEnoughPermissions(val user: String) : ControllerError
data class CantDeleteYourself(val user: String, val userToRemove: String) : ControllerError
data class AuthenticationError(val message: String) : ControllerError
data class NotFound(val id: String) : HttpError {
    override fun error(): String = "Not found $id"
}
class BadRequest(val problem: String) : ControllerError
class InvalidQueryParameter(param: String, value: String, allowed: List<String>?) : ControllerError {
    private val baseMessage = "invalid query param[$param]: $value"
    val message: String = allowed._fold({ baseMessage }, { "$baseMessage\nallowed values: $it" })
}

class InvalidTaskType(val type: String) : IllegalArgumentException("Invalid task type: $type")
class InvalidGameType(val type: String) : IllegalArgumentException("Invalid game type: $type")

interface HttpError : ControllerError {
    fun error(): String
}

data class JsonParseError(val json: String, val path: String, val error: String? = null) : HttpError {
    override fun error(): String = "ParsedJson: ${json}\nPath: $path Error: $error"
}

data class RaiderIoError(
    val statusCode: Int,
    val error: String,
    val message: String
) : HttpError {
    override fun error(): String = "$message. $error: $statusCode"
}

class NotPublished(val id: String) : ControllerError
data object TooMuchViews : ControllerError
data object TooMuchEntities : ControllerError
data object UserWithoutRoles : ControllerError
data object ExtraArgumentsWrongType : ControllerError
data object GuildViewMoreThanTwoEntities : ControllerError

suspend fun ApplicationCall.respondLogging(error: String) {
    val logger = LoggerFactory.getLogger("ktor.application")
    logger.error(error)
    respond(HttpStatusCode.InternalServerError, error)
}

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
        is JsonParseError -> respondLogging(error.error())
        is RaiderIoError -> respondLogging(error.error())
        is InvalidQueryParameter -> respond(BadRequest, error.message)
        is AuthenticationError -> respond(Unauthorized, error.message)
        is DatabaseError -> respondLogging(error.toString()) //TODO: improve
        is HttpError -> TODO()
        is CantDeleteYourself -> respond(BadRequest, "can't delete your credentials")
        is CantFeatureView -> respond(Forbidden, "not enough permissions to feature a view")
        is UserWithoutRoles -> TODO()
    }
}
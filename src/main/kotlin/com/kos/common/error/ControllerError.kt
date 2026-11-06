package com.kos.common.error

import com.kos.common._fold
import io.ktor.http.*
import io.ktor.server.application.*

sealed interface ControllerError
data object CantFeatureView : ControllerError
data object NotAuthorized : ControllerError
data class NotEnoughPermissions(val user: String) : ControllerError
data class CantDeleteYourself(val user: String, val userToRemove: String) : ControllerError
data object ThirdPartyUnavailable : ControllerError
data class UserAlreadyExists(val userName: String) : ControllerError
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

sealed interface CreateViewError : ControllerError
sealed interface EditViewError : ControllerError
sealed interface ViewEntitiesError : CreateViewError, EditViewError

data object TooMuchViews : CreateViewError
data object ExtraArgumentsWrongType : CreateViewError
data object GuildViewMoreThanOneEntity : CreateViewError
data object TooMuchEntities : ViewEntitiesError
data object UserWithoutRoles : ViewEntitiesError
data object EntitiesDoNotMatchGame : ViewEntitiesError
data object InvalidRegion : ViewEntitiesError

fun ControllerError.toProblem(): Problem =
    when (this) {
        is NotFound -> Problem(HttpStatusCode.NotFound, "NOT_FOUND", "Resource not found", id)
        is NotAuthorized -> Problem(HttpStatusCode.Unauthorized, "NOT_AUTHENTICATED", "Not authenticated")
        is NotEnoughPermissions -> Problem(HttpStatusCode.Forbidden, "FORBIDDEN", "Not enough permissions")
        is NotPublished -> Problem(HttpStatusCode.BadRequest, "VIEW_NOT_PUBLISHED", "View not published")
        is TooMuchViews -> Problem(HttpStatusCode.BadRequest, "TOO_MANY_VIEWS", "Too many views")
        is ExtraArgumentsWrongType ->
            Problem(HttpStatusCode.BadRequest, "EXTRA_ARGUMENTS_WRONG_TYPE", "Wrong extra arguments type")

        is TooMuchEntities -> Problem(HttpStatusCode.BadRequest, "TOO_MANY_ENTITIES", "Too many entities in a view")
        is BadRequest -> Problem(HttpStatusCode.BadRequest, "BAD_REQUEST", "Bad request", problem)
        is GuildViewMoreThanOneEntity ->
            Problem(HttpStatusCode.BadRequest, "GUILD_VIEW_MORE_THAN_ONE_ENTITY", "Guild views must have only 1 entity")

        is EntitiesDoNotMatchGame ->
            Problem(HttpStatusCode.BadRequest, "ENTITIES_DO_NOT_MATCH_GAME", "Entities don't match the view's game")

        is InvalidRegion ->
            Problem(HttpStatusCode.BadRequest, "INVALID_REGION", "Region must be one of eu, us, kr, tw")

        is InvalidQueryParameter ->
            Problem(HttpStatusCode.BadRequest, "INVALID_QUERY_PARAMETER", "Invalid query parameter", message)

        is CantDeleteYourself ->
            Problem(HttpStatusCode.BadRequest, "CANT_DELETE_YOURSELF", "Can't delete your own credentials")

        is CantFeatureView ->
            Problem(HttpStatusCode.Forbidden, "CANT_FEATURE_VIEW", "Not enough permissions to feature a view")

        is UserWithoutRoles -> Problem(
            HttpStatusCode.Forbidden,
            "USER_WITHOUT_ROLES",
            "User does not have sufficient roles"
        )

        is UserAlreadyExists -> Problem(HttpStatusCode.Conflict, "USER_ALREADY_EXISTS", "User already exists")
        is ThirdPartyUnavailable ->
            Problem(
                HttpStatusCode.ServiceUnavailable,
                "THIRD_PARTY_UNAVAILABLE",
                "A third party service is unavailable"
            )
    }

suspend fun ApplicationCall.respondWithHandledError(error: ControllerError) =
    respondProblem(error.toProblem())

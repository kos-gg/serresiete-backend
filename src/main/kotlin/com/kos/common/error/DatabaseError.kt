package com.kos.common.error

import com.kos.auth.InsertAuthToken
import com.kos.views.Game

sealed class DatabaseError
data class InsertError(val message: String) : DatabaseError()

fun DatabaseError.toEntityResolverError(game: Game, message: String): ServiceError =
    ResolveEntityError(game, message)

fun DatabaseError.toAuthError(message: String): ControllerError =
    InsertAuthToken(message)

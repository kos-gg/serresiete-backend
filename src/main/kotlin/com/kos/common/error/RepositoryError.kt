package com.kos.common.error

import com.kos.views.Game

data class RepositoryError(val message: String)

fun RepositoryError.toEntityResolverError(game: Game, message: String): ServiceError =
    ResolveEntityError(game, message)

fun RepositoryError.toAuthTokenError(message: String): ServiceError =
    AuthTokenError(message)

fun RepositoryError.toEventPersistenceError(): ServiceError =
    EventPersistenceError(message)

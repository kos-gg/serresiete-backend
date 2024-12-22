package com.kos.views

import arrow.core.Either
import arrow.core.raise.either
import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.common.*
import com.kos.eventsourcing.events.Operation

class ViewsController(
    private val viewsService: ViewsService,
) {

    suspend fun getViews(
        client: String?,
        activities: Set<Activity>,
        game: Game?,
        featured: Boolean,
        page: Int?,
        limit: Int?,
        includeMetadata: Boolean
    ): Either<ControllerError, Pair<ViewMetadata?, List<SimpleView>>> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if (activities.contains(Activities.getAnyViews)) {
                    val anyViews = viewsService.getViews(
                        game,
                        featured,
                        page,
                        limit
                    )
                    Either.Right(
                        when (includeMetadata) {
                            false -> Pair(null, anyViews.second)
                            true -> anyViews
                        }
                    )
                } else if (activities.contains(Activities.getOwnViews))
                    Either.Right(
                        Pair(
                            null,
                            viewsService.getOwnViews(client)
                        )
                    )
                else Either.Left(NotEnoughPermissions(client))
            }
        }
    }

    suspend fun getView(
        client: String?,
        id: String,
        activities: Set<Activity>
    ): Either<ControllerError, View> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                return when (val maybeView = viewsService.get(id)) {
                    null -> Either.Left(NotFound(id))
                    else -> {
                        if ((maybeView.owner == client && activities.contains(Activities.getOwnView))
                            || activities.contains(Activities.getAnyView)
                        ) Either.Right(maybeView)
                        else Either.Left(NotEnoughPermissions(client))
                    }
                }
            }
        }
    }

    suspend fun getViewData(
        client: String?,
        id: String,
        activities: Set<Activity>
    ): Either<ControllerError, ViewData> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                return when (val maybeView = viewsService.get(id)) {
                    null -> Either.Left(NotFound(id))
                    else -> {
                        if (activities.contains(Activities.getViewData) && maybeView.published) {
                            either { ViewData(maybeView.name, viewsService.getData(maybeView).bind()) }
                        } else if (!maybeView.published) Either.Left(NotPublished(id))
                        else Either.Left(NotEnoughPermissions(client))
                    }
                }
            }
        }
    }

    suspend fun getViewCachedData(
        client: String?,
        id: String,
        activities: Set<Activity>
    ): Either<ControllerError, ViewData> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                return when (val maybeView = viewsService.getSimple(id)) {
                    null -> Either.Left(NotFound(id))
                    else -> {
                        if (activities.contains(Activities.getViewCachedData) && maybeView.published)
                            either { ViewData(maybeView.name, viewsService.getCachedData(maybeView).bind()) }
                        else if (!maybeView.published) Either.Left(NotPublished(id))
                        else Either.Left(NotEnoughPermissions(client))
                    }
                }
            }
        }
    }

    suspend fun createView(
        client: String?,
        request: ViewRequest,
        activities: Set<Activity>
    ): Either<ControllerError, Operation> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if (activities.contains(Activities.createViews)) {
                    when (val res = viewsService.create(client, request)) {
                        is Either.Right -> Either.Right(res.value)
                        is Either.Left -> res
                    }
                } else Either.Left(NotEnoughPermissions(client))
            }
        }
    }

    suspend fun editView(
        client: String?,
        request: ViewRequest,
        id: String,
        activities: Set<Activity>
    ): Either<ControllerError, Operation> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> when (val maybeView = viewsService.get(id)) {
                null -> Either.Left(NotFound(id))
                else -> {
                    if ((maybeView.owner == client && activities.contains(Activities.editOwnView))
                        || activities.contains(Activities.editAnyView)
                    ) {
                        viewsService.edit(client, maybeView.id, request)
                    } else Either.Left(NotEnoughPermissions(client))
                }
            }
        }
    }

    suspend fun patchView(
        client: String?,
        request: ViewPatchRequest,
        id: String,
        activities: Set<Activity>
    ): Either<ControllerError, Operation> {
        //TODO: We can propagate view fields to those who are optional from patch, or we can keep it like this to display which fields we modified
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> when (val maybeView = viewsService.get(id)) {
                null -> Either.Left(NotFound(id))
                else -> {
                    if ((maybeView.owner == client && activities.contains(Activities.editOwnView))
                        || activities.contains(Activities.editAnyView)
                    ) {
                        viewsService.patch(client, maybeView.id, request)
                    } else Either.Left(NotEnoughPermissions(client))
                }
            }
        }
    }

    suspend fun deleteView(
        client: String?,
        id: String,
        activities: Set<Activity>
    ): Either<ControllerError, ViewDeleted> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> when (val maybeView = viewsService.get(id)) {
                null -> Either.Left(NotFound(id))
                else -> {
                    if ((maybeView.owner == client && activities.contains(Activities.deleteOwnView))
                        || activities.contains(Activities.deleteAnyView)
                    ) {
                        Either.Right(viewsService.delete(maybeView.id))
                    } else Either.Left(NotEnoughPermissions(client))
                }
            }
        }
    }

}
package com.kos.views

import com.kos.common.WithLogger
import com.kos.entities.EntitiesService
import com.kos.entities.domain.WowGuild
import com.kos.entities.domain.ResolvedEntities
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.domain.WowForeverEntityRequest
import com.kos.eventsourcing.events.*
import com.kos.views.repository.ViewsRepository

class ViewsEventService(
    private val viewsRepository: ViewsRepository,
    private val entitiesService: EntitiesService
) : WithLogger("ViewsEventService") {

    suspend fun createView(viewToBeCreatedEvent: ViewToBeCreatedEvent): ViewCreatedEvent {
        val resolved = resolveViewEntities(viewToBeCreatedEvent)

        val inserted = entitiesService
            .insert(resolved.entities.map { it.first }, viewToBeCreatedEvent.game)

        val entities = inserted.zip(resolved.entities.map { it.second }) +
                resolved.existing

        val view = viewsRepository.create(
            viewToBeCreatedEvent.id,
            viewToBeCreatedEvent.name,
            viewToBeCreatedEvent.owner,
            entities.map { it.first.id to it.second },
            viewToBeCreatedEvent.game,
            viewToBeCreatedEvent.featured,
            viewToBeCreatedEvent.extraArguments
        )

        resolved.guild?.let {
            entitiesService.insertGuild(it, view.id, viewToBeCreatedEvent.game)
        }

        return ViewCreatedEvent.fromSimpleView(view)
    }

    private suspend fun resolveViewEntities(
        event: ViewToBeCreatedEvent
    ): ResolvedEntities {
        return when (val trackedGuild = findTrackedGuild(event)) {
            null -> entitiesService.resolveEntities(event.entities, event.game, event.extraArguments)
            else -> {
                val (guildPayload, sourceViewId) = trackedGuild
                val sourceView = checkNotNull(viewsRepository.get(sourceViewId)) {
                    "tracked guild's source view $sourceViewId no longer exists"
                }
                val existing = sourceView.entitiesIds.mapNotNull { id ->
                    entitiesService.get(id, event.game)?.let { entity ->
                        entity to viewsRepository.getViewEntity(sourceViewId, id)?.alias
                    }
                }
                ResolvedEntities(
                    entities = emptyList(),
                    existing = existing,
                    unchecked = emptyList(),
                    guild = guildPayload
                )
            }
        }
    }

    private suspend fun findTrackedGuild(event: ViewToBeCreatedEvent): Pair<WowGuild, String>? =
        event.entities.firstOrNull()
            ?.takeIf { (event.extraArguments as? WowExtraArguments)?.isGuild == true }
            ?.let { guild ->
                when (event.game) {
                    Game.WOW -> (guild as WowEntityRequest)
                        .let { entitiesService.findTrackedGuild(it.name, it.realm, it.region, Game.WOW) }

                    Game.WOW_FOREVER -> (guild as WowForeverEntityRequest)
                        .let { entitiesService.findTrackedGuild(it.name, null, it.region, Game.WOW_FOREVER) }

                    Game.WOW_HC, Game.LOL -> null
                }
            }

    suspend fun editView(viewToBeEditedEvent: ViewToBeEditedEvent): ViewEditedEvent {
        val resolved =
            entitiesService.resolveEntities(
                viewToBeEditedEvent.entities,
                viewToBeEditedEvent.game
            )

        val inserted = entitiesService
            .insert(resolved.entities.map { it.first }, viewToBeEditedEvent.game)

        val entities = inserted.zip(resolved.entities.map { it.second }) +
                resolved.existing
        val viewModified = viewsRepository.edit(
            viewToBeEditedEvent.id,
            viewToBeEditedEvent.name,
            viewToBeEditedEvent.published,
            entities.map { it.first.id to it.second },
            viewToBeEditedEvent.featured
        )

        return ViewEditedEvent.fromViewModified(viewToBeEditedEvent.id, viewToBeEditedEvent.game, viewModified)
    }

    suspend fun patchView(viewToBePatchedEvent: ViewToBePatchedEvent): ViewPatchedEvent {
        val entitiesToInsert = viewToBePatchedEvent.entities?.let { entitiesToInsert ->
            val resolved =
                entitiesService.resolveEntities(
                    entitiesToInsert,
                    viewToBePatchedEvent.game
                )

            val inserted = entitiesService
                .insert(resolved.entities.map { it.first }, viewToBePatchedEvent.game)

            inserted.zip(resolved.entities.map { it.second }) +
                    resolved.existing
        }
        val patchedView = viewsRepository.patch(
            viewToBePatchedEvent.id,
            viewToBePatchedEvent.name,
            viewToBePatchedEvent.published,
            entitiesToInsert?.map { it.first.id to it.second },
            viewToBePatchedEvent.featured
        )

        return ViewPatchedEvent.fromViewPatched(viewToBePatchedEvent.id, viewToBePatchedEvent.game, patchedView)
    }

    suspend fun deleteView(event: ViewToBeDeletedEvent): ViewDeletedEvent {
        viewsRepository.delete(event.id)
        return ViewDeletedEvent(
            event.id,
            event.name,
            event.owner,
            event.entities,
            event.published,
            event.game,
            event.featured
        )
    }
}

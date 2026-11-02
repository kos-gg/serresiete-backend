package com.kos.views

import com.kos.common.WithLogger
import com.kos.entities.EntitiesService
import com.kos.entities.domain.ResolvedEntities
import com.kos.entities.domain.WowEntityRequest
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.views.repository.ViewsRepository

class ViewsEventService(
    private val viewsRepository: ViewsRepository,
    private val entitiesService: EntitiesService,
    private val eventStore: EventStore
) : WithLogger("ViewsEventService") {

    suspend fun createView(
        operationId: String,
        aggregateRoot: String,
        viewToBeCreatedEvent: ViewToBeCreatedEvent
    ): Operation {
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

        val event = Event(
            aggregateRoot,
            operationId,
            ViewCreatedEventEvent.fromSimpleView(view)
        )
        return eventStore.save(event)
    }

    private suspend fun resolveViewEntities(
        event: ViewToBeCreatedEvent
    ): ResolvedEntities {
        val guildReq = (event.extraArguments as? WowExtraArguments)
            ?.takeIf { it.isGuild && event.game == Game.WOW }
            ?.let { event.entities.first() as WowEntityRequest }

        val trackedGuild = guildReq?.let { req ->
            entitiesService.findTrackedGuild(req.name, req.realm, req.region, Game.WOW)
        }

        return when (trackedGuild) {
            null -> entitiesService.resolveEntities(event.entities, event.game, event.extraArguments)
            else -> {
                val (guildPayload, sourceViewId) = trackedGuild
                val sourceView = checkNotNull(viewsRepository.get(sourceViewId)) {
                    "tracked guild's source view $sourceViewId no longer exists"
                }
                val existing = sourceView.entitiesIds.mapNotNull { id ->
                    entitiesService.get(id, Game.WOW)?.let { entity ->
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

    suspend fun editView(
        operationId: String,
        aggregateRoot: String,
        viewToBeEditedEvent: ViewToBeEditedEvent
    ): Operation {
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

        val event = Event(
            aggregateRoot,
            operationId,
            ViewEditedEventEvent.fromViewModified(operationId, viewToBeEditedEvent.game, viewModified)
        )
        return eventStore.save(event)
    }

    suspend fun patchView(
        operationId: String,
        aggregateRoot: String,
        viewToBePatchedEvent: ViewToBePatchedEvent
    ): Operation {
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

        val event = Event(
            aggregateRoot,
            operationId,
            ViewPatchedEventEvent.fromViewPatched(operationId, viewToBePatchedEvent.game, patchedView)
        )
        return eventStore.save(event)
    }

    suspend fun deleteView(
        operationId: String,
        aggregateRoot: String,
        event: ViewToBeDeletedEvent
    ): Operation {
        viewsRepository.delete(event.id)
        val completionEvent = Event(
            aggregateRoot,
            operationId,
            ViewDeletedEvent(
                event.id,
                event.name,
                event.owner,
                event.entities,
                event.published,
                event.game,
                event.featured
            )
        )
        return eventStore.save(completionEvent)
    }
}

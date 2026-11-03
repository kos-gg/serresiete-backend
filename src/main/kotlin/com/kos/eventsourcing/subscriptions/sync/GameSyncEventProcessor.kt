package com.kos.eventsourcing.subscriptions.sync

import com.kos.common.WithLogger
import com.kos.entities.EntitiesService
import com.kos.entities.sync.EntitySynchronizer
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.subscriptions.EventProcessOutcome

class GameSyncEventProcessor(
    private val eventWithVersion: EventWithVersion,
    private val entitiesService: EntitiesService,
    private val synchronizer: EntitySynchronizer
) : EventProcessor, WithLogger("eventSubscription.syncEntitiesProcessor") {

    private val game = synchronizer.game

    override suspend fun process(): EventProcessOutcome {
        return when (eventWithVersion.event.eventData.eventType) {
            EventType.VIEW_CREATED, EventType.VIEW_EDITED, EventType.VIEW_PATCHED -> {
                val payload = eventWithVersion.event.eventData as ViewSyncTriggerEvent
                when (payload.game) {
                    game -> synchronizeView(payload.id, payload.entities)
                    else -> EventProcessOutcome.Skipped
                }
            }

            EventType.REQUEST_TO_BE_SYNCED -> {
                val payload = eventWithVersion.event.eventData as RequestToBeSynced
                when (payload.game) {
                    game -> {
                        logger.debug("processing event v${eventWithVersion.version}")
                        val resolved = entitiesService.resolveEntities(listOf(payload.request), payload.game)
                        val inserted = entitiesService.insert(resolved.entities.map { it.first }, payload.game)
                        val entities = inserted.zip(resolved.entities.map { it.second }) + resolved.existing
                        val errors = synchronizer.synchronize(entities.map { it.first })
                        if (errors.isNotEmpty()) {
                            logger.warn("sync completed with non-fatal errors: ${errors.joinToString("; ") { it.error() }}")
                        }
                        EventProcessOutcome.Processed(EntitySyncCompletedEvent(payload.request, payload.game))
                    }

                    else -> EventProcessOutcome.Skipped
                }
            }

            else -> EventProcessOutcome.Skipped
        }
    }

    private suspend fun synchronizeView(
        viewId: String,
        entities: List<Long>?
    ): EventProcessOutcome {
        logger.debug("processing event v${eventWithVersion.version}")
        if (entities != null) {
            val resolved = entities.mapNotNull { entitiesService.get(it, game) }
            val errors = synchronizer.synchronize(resolved)
            if (errors.isNotEmpty()) {
                logger.warn("sync completed with non-fatal errors: ${errors.joinToString("; ") { it.error() }}")
            }
        }
        return EventProcessOutcome.Processed(ViewSyncCompletedEvent(viewId))
    }
}

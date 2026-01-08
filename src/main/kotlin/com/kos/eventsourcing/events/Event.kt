package com.kos.eventsourcing.events

import com.kos.entities.domain.CreateEntityRequest
import com.kos.views.*
import kotlinx.serialization.Serializable

enum class EventType {
    VIEW_TO_BE_CREATED {
        override fun toString(): String = "viewToBeCreated"
    },
    VIEW_TO_BE_EDITED {
        override fun toString(): String = "viewToBeEdited"
    },
    VIEW_TO_BE_PATCHED {
        override fun toString(): String = "viewToBePatched"
    },
    VIEW_CREATED {
        override fun toString(): String = "viewCreated"
    },
    VIEW_EDITED {
        override fun toString(): String = "viewEdited"
    },
    VIEW_PATCHED {
        override fun toString(): String = "viewPatched"
    },
    VIEW_DELETED {
        override fun toString(): String = "viewDeleted"
    },
    REQUEST_TO_BE_SYNCED {
        override fun toString(): String = "requestToBeSynced"
    };

    companion object {
        fun fromString(string: String): EventType {
            return when (string) {
                "viewToBeCreated" -> VIEW_TO_BE_CREATED
                "viewToBeEdited" -> VIEW_TO_BE_EDITED
                "viewToBePatched" -> VIEW_TO_BE_PATCHED
                "viewCreated" -> VIEW_CREATED
                "viewEdited" -> VIEW_EDITED
                "viewPatched" -> VIEW_PATCHED
                "viewDeleted" -> VIEW_DELETED
                "requestToBeSynced" -> REQUEST_TO_BE_SYNCED
                else -> throw IllegalArgumentException("error parsing EventType: $string")
            }
        }
    }
}

sealed interface EventData {
    val eventType: EventType
}

@Serializable
data class ViewToBeCreatedEvent(
    val id: String,
    val name: String,
    val published: Boolean,
    val entities: List<CreateEntityRequest>,
    val game: Game,
    val owner: String,
    val featured: Boolean,
    val extraArguments: ViewExtraArguments?
) : EventData {
    override val eventType: EventType = EventType.VIEW_TO_BE_CREATED
}

@Serializable
data class ViewToBeEditedEvent(
    val id: String,
    val name: String,
    val published: Boolean,
    val entities: List<CreateEntityRequest>,
    val game: Game,
    val featured: Boolean
) : EventData {
    override val eventType: EventType = EventType.VIEW_TO_BE_EDITED
}

@Serializable
data class ViewToBePatchedEvent(
    val id: String,
    val name: String?,
    val published: Boolean?,
    val entities: List<CreateEntityRequest>?,
    val game: Game,
    val featured: Boolean?
) : EventData {
    override val eventType: EventType = EventType.VIEW_TO_BE_PATCHED
}

@Serializable
data class ViewCreatedEvent(
    val id: String,
    val name: String,
    val owner: String,
    val entities: List<Long>,
    val published: Boolean,
    val game: Game,
    val featured: Boolean,
    val extraArguments: ViewExtraArguments?
) : EventData {
    override val eventType: EventType = EventType.VIEW_CREATED

    companion object {
        fun fromSimpleView(simpleView: SimpleView) = ViewCreatedEvent(
            simpleView.id,
            simpleView.name,
            simpleView.owner,
            simpleView.entitiesIds,
            simpleView.published,
            simpleView.game,
            simpleView.featured,
            simpleView.extraArguments
        )
    }
}

@Serializable
data class ViewEditedEvent(
    val id: String,
    val name: String,
    val entities: List<Long>,
    val published: Boolean,
    val game: Game,
    val featured: Boolean
) : EventData {
    override val eventType: EventType = EventType.VIEW_EDITED

    companion object {
        fun fromViewModified(id: String, game: Game, viewModified: ViewModified) = ViewEditedEvent(
            id,
            viewModified.name,
            viewModified.entities,
            viewModified.published,
            game,
            viewModified.featured
        )
    }
}

@Serializable
data class ViewPatchedEvent(
    val id: String,
    val name: String?,
    val entities: List<Long>?,
    val published: Boolean?,
    val game: Game,
    val featured: Boolean?
) : EventData {
    override val eventType: EventType = EventType.VIEW_PATCHED

    companion object {
        fun fromViewPatched(id: String, game: Game, viewPatched: ViewPatched) = ViewPatchedEvent(
            id,
            viewPatched.name,
            viewPatched.entities,
            viewPatched.published,
            game,
            viewPatched.featured
        )
    }
}

@Serializable
data class ViewDeletedEvent(
    val id: String,
    val name: String,
    val owner: String,
    val entities: List<Long>,
    val published: Boolean,
    val game: Game,
    val featured: Boolean
) : EventData {
    override val eventType: EventType = EventType.VIEW_DELETED
}

@Serializable
data class RequestToBeSynced(
    val request: CreateEntityRequest,
    val game: Game
): EventData {
    override val eventType: EventType = EventType.REQUEST_TO_BE_SYNCED
}

@Serializable
data class Event(
    val aggregateRoot: String,
    val operationId: String,
    val eventData: EventData
)

@Serializable
data class Operation(
    val id: String,
    val aggregateRoot: String,
    val type: EventType
)

data class EventWithVersion(val version: Long, val event: Event)
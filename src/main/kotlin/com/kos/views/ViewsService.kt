package com.kos.views

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kos.clients.domain.Data
import com.kos.common.*
import com.kos.credentials.CredentialsService
import com.kos.datacache.DataCacheService
import com.kos.entities.CreateEntityRequest
import com.kos.entities.EntitiesService
import com.kos.entities.EntityWithAlias
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.views.repository.ViewsRepository
import java.util.*

class ViewsService(
    private val viewsRepository: ViewsRepository,
    private val entitiesService: EntitiesService,
    private val dataCacheService: DataCacheService,
    private val credentialsService: CredentialsService,
    private val eventStore: EventStore
) {

    suspend fun getOwnViews(owner: String): List<SimpleView> = viewsRepository.getOwnViews(owner)
    suspend fun getViews(
        game: Game?,
        featured: Boolean,
        page: Int?,
        limit: Int?
    ): Pair<ViewMetadata, List<SimpleView>> =
        viewsRepository.getViews(game, featured, page, limit)

    suspend fun get(id: String): View? {
        return when (val simpleView = viewsRepository.get(id)) {
            null -> null
            else -> {
                View(
                    simpleView.id,
                    simpleView.name,
                    simpleView.owner,
                    simpleView.published,
                    simpleView.entitiesIds.mapNotNull {
                        entitiesService.get(it, simpleView.game)
                    }.mapNotNull { entity ->
                        viewsRepository.getViewEntity(simpleView.id, entity.id)?.let {
                            EntityWithAlias(entity, it.alias)
                        }
                    },
                    simpleView.game,
                    simpleView.featured
                )
            }
        }
    }

    suspend fun getSimple(id: String): SimpleView? = viewsRepository.get(id)

    suspend fun create(owner: String, request: ViewRequest): Either<ControllerError, Operation> {
        return either {
            ensureMaxNumberOfViews(owner).bind()
            ensureMaxNumberOfEntities(owner, request.entities).bind()
            ensureRequest(request).bind()

            val operationId = UUID.randomUUID().toString()
            val aggregateRoot = "/credentials/$owner"
            val event = Event(
                aggregateRoot,
                operationId,
                ViewToBeCreatedEvent(
                    operationId,
                    request.name,
                    request.published,
                    request.entities,
                    request.game,
                    owner,
                    request.featured,
                    request.extraArguments
                )
            )
            eventStore.save(event)
        }
    }

    suspend fun createView(
        operationId: String,
        aggregateRoot: String,
        viewToBeCreatedEvent: ViewToBeCreatedEvent
    ): Either<InsertError, Operation> {
        return either {
            val entities =
                entitiesService.createAndReturnIds(viewToBeCreatedEvent.entities, viewToBeCreatedEvent.game).bind()
            val view = viewsRepository.create(
                viewToBeCreatedEvent.id,
                viewToBeCreatedEvent.name,
                viewToBeCreatedEvent.owner,
                entities.map { it.first.id to it.second },
                viewToBeCreatedEvent.game,
                viewToBeCreatedEvent.featured,
                viewToBeCreatedEvent.extraArguments
            )
            val event = Event(
                aggregateRoot,
                operationId,
                ViewCreatedEvent.fromSimpleView(view)
            )
            eventStore.save(event)
        }
    }

    suspend fun edit(owner: String, id: String, request: ViewRequest): Either<ControllerError, Operation> {
        return either {
            ensureMaxNumberOfEntities(owner, request.entities).bind()

            val aggregateRoot = "/credentials/$owner"
            val event = Event(
                aggregateRoot,
                id,
                ViewToBeEditedEvent(
                    id,
                    request.name,
                    request.published,
                    request.entities,
                    request.game,
                    request.featured
                )
            )
            eventStore.save(event)
        }
    }

    suspend fun editView(
        operationId: String,
        aggregateRoot: String,
        viewToBeEditedEvent: ViewToBeEditedEvent
    ): Either<ControllerError, Operation> {
        return either {
            val entities =
                entitiesService.createAndReturnIds(viewToBeEditedEvent.entities, viewToBeEditedEvent.game).bind()
            val viewModified =
                viewsRepository.edit(
                    viewToBeEditedEvent.id,
                    viewToBeEditedEvent.name,
                    viewToBeEditedEvent.published,
                    entities.map { it.first.id to it.second },
                    viewToBeEditedEvent.featured
                )
            val event = Event(
                aggregateRoot,
                operationId,
                ViewEditedEvent.fromViewModified(operationId, viewToBeEditedEvent.game, viewModified)
            )
            eventStore.save(event)
        }

    }

    suspend fun patch(owner: String, id: String, request: ViewPatchRequest): Either<ControllerError, Operation> {
        return either {
            ensureMaxNumberOfEntities(owner, request.entities).bind()

            val aggregateRoot = "/credentials/$owner"
            val event = Event(
                aggregateRoot,
                id,
                ViewToBePatchedEvent(
                    id,
                    request.name,
                    request.published,
                    request.entities,
                    request.game,
                    request.featured
                )
            )

            eventStore.save(event)
        }
    }

    suspend fun patchView(
        operationId: String,
        aggregateRoot: String,
        viewToBePatchedEvent: ViewToBePatchedEvent
    ): Either<InsertError, Operation> {
        return either {
            val entitiesToInsert = viewToBePatchedEvent.entities?.let { entitiesToInsert ->
                entitiesService.createAndReturnIds(entitiesToInsert, viewToBePatchedEvent.game).bind()
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
                ViewPatchedEvent.fromViewPatched(operationId, viewToBePatchedEvent.game, patchedView)
            )
            eventStore.save(event)
        }
    }

    suspend fun delete(owner: String, viewToDelete: SimpleView): Operation {
        val aggregateRoot = "/credentials/$owner"
        val event = Event(
            aggregateRoot,
            viewToDelete.id,
            ViewDeletedEvent(
                viewToDelete.id,
                viewToDelete.name,
                viewToDelete.owner,
                viewToDelete.entitiesIds,
                viewToDelete.published,
                viewToDelete.game,
                viewToDelete.featured
            )
        )

        viewsRepository.delete(viewToDelete.id) //TODO: encapsular en either lo que retorna delete
        return eventStore.save(event)
    }

    suspend fun getData(view: View): Either<HttpError, List<Data>> =
        dataCacheService.getData(view.entities.map { it.value.id }, oldFirst = false)

    suspend fun getCachedData(simpleView: SimpleView) =
        dataCacheService.getData(simpleView.entitiesIds, oldFirst = true)

    private suspend fun getMaxNumberOfViewsByRole(owner: String): Either<UserWithoutRoles, Int> =
        when (val maxNumberOfViews = credentialsService.getUserRoles(owner).maxOfOrNull { it.maxNumberOfViews }) {
            null -> Either.Left(UserWithoutRoles)
            else -> Either.Right(maxNumberOfViews)
        }

    private suspend fun getMaxNumberOfEntitiesByRole(owner: String): Either<UserWithoutRoles, Int> =
        when (val maxNumberOfEntities =
            credentialsService.getUserRoles(owner).maxOfOrNull { it.maxNumberOfEntities }) {
            null -> Either.Left(UserWithoutRoles)
            else -> Either.Right(maxNumberOfEntities)
        }

    private suspend fun ensureMaxNumberOfViews(owner: String): Either<ControllerError, Unit> {
        return either {
            val ownerMaxViews = getMaxNumberOfViewsByRole(owner).bind()
            ensure(viewsRepository.getOwnViews(owner).size < ownerMaxViews) { TooMuchViews }
        }
    }

    private suspend fun ensureMaxNumberOfEntities(
        owner: String,
        entities: List<CreateEntityRequest>?
    ): Either<ControllerError, Unit> {
        return either {
            val ownerMaxNumberOfEntities = getMaxNumberOfEntitiesByRole(owner).bind()
            entities?.let { entitiesToInsert ->
                ensure(entitiesToInsert.size <= ownerMaxNumberOfEntities) { TooMuchEntities }
            }
        }
    }

    private fun ensureRequest(
        request: ViewRequest
    ): Either<ControllerError, Unit> {
        return either {
            when (request.game) {
                Game.WOW_HC -> ensure(request.extraArguments == null || request.extraArguments is WowHardcoreExtraArguments) { ExtraArgumentsWrongType }
                Game.LOL -> Either.Right(Unit)
                Game.WOW -> ensure(request.extraArguments == null || request.extraArguments is WowExtraArguments) { ExtraArgumentsWrongType }
            }
        }
    }
}
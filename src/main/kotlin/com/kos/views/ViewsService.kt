package com.kos.views

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kos.characters.CharactersService
import com.kos.clients.domain.Data
import com.kos.common.*
import com.kos.credentials.CredentialsService
import com.kos.datacache.DataCacheService
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.views.repository.ViewsRepository
import java.util.*

class ViewsService(
    private val viewsRepository: ViewsRepository,
    private val charactersService: CharactersService,
    private val dataCacheService: DataCacheService,
    private val credentialsService: CredentialsService,
    private val eventStore: EventStore
) {

    suspend fun getOwnViews(owner: String): List<SimpleView> = viewsRepository.getOwnViews(owner)
    suspend fun getViews(game: Game?, featured: Boolean, page: Int?, limit: Int?): List<SimpleView> =
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
                    simpleView.characterIds.mapNotNull {
                        charactersService.get(it, simpleView.game)
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
            val ownerMaxViews = getMaxNumberOfViewsByRole(owner).bind()
            ensure(viewsRepository.getOwnViews(owner).size < ownerMaxViews) { TooMuchViews }
            val ownerMaxCharacters = getMaxNumberOfCharactersByRole(owner).bind()
            ensure(request.characters.size <= ownerMaxCharacters) { TooMuchCharacters }
            val operationId = UUID.randomUUID().toString()
            val aggregateRoot = "/credentials/$owner"
            val event = Event(
                aggregateRoot,
                operationId,
                ViewToBeCreatedEvent(
                    operationId,
                    request.name,
                    request.published,
                    request.characters,
                    request.game,
                    owner,
                    request.featured
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
            val characterIds =
                charactersService.createAndReturnIds(viewToBeCreatedEvent.characters, viewToBeCreatedEvent.game).bind()
            val view = viewsRepository.create(
                viewToBeCreatedEvent.id,
                viewToBeCreatedEvent.name,
                viewToBeCreatedEvent.owner,
                characterIds,
                viewToBeCreatedEvent.game,
                viewToBeCreatedEvent.featured
            )
            val event = Event(
                aggregateRoot,
                operationId,
                ViewCreatedEvent.fromSimpleView(view)
            )
            eventStore.save(event)
        }
    }

    suspend fun edit(client: String, id: String, request: ViewRequest): Either<ControllerError, Operation> {
        return either {
            val ownerMaxCharacters = getMaxNumberOfCharactersByRole(client).bind()
            ensure(request.characters.size <= ownerMaxCharacters) { TooMuchCharacters }
            val aggregateRoot = "/credentials/$client"
            val event = Event(
                aggregateRoot,
                id,
                ViewToBeEditedEvent(
                    id,
                    request.name,
                    request.published,
                    request.characters,
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
            val characters =
                charactersService.createAndReturnIds(viewToBeEditedEvent.characters, viewToBeEditedEvent.game).bind()
            val viewModified =
                viewsRepository.edit(
                    viewToBeEditedEvent.id,
                    viewToBeEditedEvent.name,
                    viewToBeEditedEvent.published,
                    characters,
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

    suspend fun patch(client: String, id: String, request: ViewPatchRequest): Either<ControllerError, Operation> {
        return either {
            val ownerMaxCharacters = getMaxNumberOfCharactersByRole(client).bind()
            request.characters?.let { charactersToInsert ->
                ensure(charactersToInsert.size <= ownerMaxCharacters) { TooMuchCharacters }
            }
            val aggregateRoot = "/credentials/$client"
            val event = Event(
                aggregateRoot,
                id,
                ViewToBePatchedEvent(
                    id,
                    request.name,
                    request.published,
                    request.characters,
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
            val charactersToInsert = viewToBePatchedEvent.characters?.let { charactersToInsert ->
                charactersService.createAndReturnIds(charactersToInsert, viewToBePatchedEvent.game).bind()
            }
            val patchedView = viewsRepository.patch(
                viewToBePatchedEvent.id,
                viewToBePatchedEvent.name,
                viewToBePatchedEvent.published,
                charactersToInsert,
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
                viewToDelete.characterIds,
                viewToDelete.published,
                viewToDelete.game,
                viewToDelete.featured
            )
        )

        viewsRepository.delete(viewToDelete.id) //TODO: encapsular en either lo que retorna delete
        return eventStore.save(event)
    }

    suspend fun getData(view: View): Either<HttpError, List<Data>> =
        dataCacheService.getData(view.characters.map { it.id }, oldFirst = false)

    suspend fun getCachedData(simpleView: SimpleView) =
        dataCacheService.getData(simpleView.characterIds, oldFirst = true)

    private suspend fun getMaxNumberOfViewsByRole(owner: String): Either<UserWithoutRoles, Int> =
        when (val maxNumberOfViews = credentialsService.getUserRoles(owner).maxOfOrNull { it.maxNumberOfViews }) {
            null -> Either.Left(UserWithoutRoles)
            else -> Either.Right(maxNumberOfViews)
        }

    private suspend fun getMaxNumberOfCharactersByRole(owner: String): Either<UserWithoutRoles, Int> =
        when (val maxNumberOfCharacters =
            credentialsService.getUserRoles(owner).maxOfOrNull { it.maxNumberOfCharacters }) {
            null -> Either.Left(UserWithoutRoles)
            else -> Either.Right(maxNumberOfCharacters)
        }
}
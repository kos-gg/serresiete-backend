package com.kos.views

import arrow.core.Either
import arrow.core.sequence
import arrow.core.traverse
import com.kos.characters.CharactersService
import com.kos.characters.WowCharacter
import com.kos.common.*
import com.kos.datacache.DataCacheService
import com.kos.raiderio.RaiderIoClient
import com.kos.raiderio.RaiderIoData
import com.kos.views.repository.ViewsRepository
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class ViewsService(
    private val viewsRepository: ViewsRepository,
    private val charactersService: CharactersService,
    private val dataCacheService: DataCacheService,
    private val raiderIoClient: RaiderIoClient
) {

    private val maxNumberOfViews: Int = 2

    suspend fun getOwnViews(owner: String): List<SimpleView> = viewsRepository.getOwnViews(owner)
    suspend fun getViews(): List<SimpleView> = viewsRepository.getViews()
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
                    simpleView.game
                )
            }
        }
    }

    suspend fun getSimple(id: String): SimpleView? = viewsRepository.get(id)

    suspend fun create(owner: String, request: ViewRequest): Either<ControllerError, ViewModified> {
        if (viewsRepository.getOwnViews(owner).size >= maxNumberOfViews) return Either.Left(TooMuchViews())
        val characterIds = charactersService.createAndReturnIds(request.characters, request.game)
        return characterIds.map { viewsRepository.create(request.name, owner, it, request.game) }
    }

    suspend fun edit(id: String, request: ViewRequest): Either<ControllerError, ViewModified> {
        val characters = charactersService.createAndReturnIds(request.characters, request.game)
        return characters.map { viewsRepository.edit(id, request.name, request.published, it) }
    }

    suspend fun patch(id: String, request: ViewPatchRequest): Either<ControllerError, ViewModified> {
        return when (val characters: Either<InsertCharacterError, List<Long>>? = request.characters.fold({ null }, { charactersRequest -> charactersService.createAndReturnIds(charactersRequest, Game.WOW)})) { //TODO: THIS NEEDS TO BE FIXED AND ITS BIG ISSUE
            null -> Either.Right(viewsRepository.patch(id, request.name, request.published, null))
            else -> characters.map { viewsRepository.patch(id, request.name, request.published, it) }
        }
    }

    suspend fun delete(id: String): ViewDeleted {
        return viewsRepository.delete(id)
    }

    suspend fun getData(view: View): Either<HttpError, List<RaiderIoData>> = coroutineScope {
        val eitherJsonErrorOrData = when (val cutoffOrError = raiderIoClient.cutoff()) {
            is Either.Left -> Either.Left(cutoffOrError.value)
            is Either.Right -> {
                val eitherErrorOrResponse = view.characters.map { char ->
                    async {
                        raiderIoClient.get(char as WowCharacter).map {//TODO: Fix
                            Pair(char.id, it)
                        }
                    }
                }.awaitAll().sequence()
                when (eitherErrorOrResponse) {
                    is Either.Left -> eitherErrorOrResponse
                    is Either.Right -> {
                        eitherErrorOrResponse.value.traverse {
                            val quantile =
                                BigDecimal(it.second.profile.mythicPlusRanks.overall.region.toDouble() / cutoffOrError.value.totalPopulation * 100).setScale(
                                    2,
                                    RoundingMode.HALF_EVEN
                                )
                            Either.Right(
                                it.second.profile.toRaiderIoData(
                                    it.first,
                                    quantile.toDouble(),
                                    it.second.specs
                                )
                            )
                        }
                    }
                }
            }
        }
        /*
        TODO: This will be used in the future again. We will make usage of every call to raiderio to avoid
        TODO: needing to retrieve data for every character again if it was called in a 1h period. This will lighten
        TODO: the scheduled task.
        eitherJsonErrorOrData.onRight {
            it.forEach { data ->
                dataCacheService.insert(
                    DataCache(
                        data.id,
                        json.encodeToString(data),
                        OffsetDateTime.now()
                    )
                )
            }
        }
        */
        eitherJsonErrorOrData
    }

    suspend fun getCachedData(simpleView: SimpleView) = dataCacheService.getData(simpleView)
}

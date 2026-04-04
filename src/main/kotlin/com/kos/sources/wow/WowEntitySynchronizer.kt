package com.kos.sources.wow

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parMap
import com.kos.clients.ClientError
import com.kos.clients.domain.*
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.DynamicCache
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.common.error.SyncProcessingError
import com.kos.common.split
import com.kos.datacache.DataCache
import com.kos.datacache.EntitySynchronizer
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.domain.Entity
import com.kos.entities.domain.WowEntity
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonRepository
import com.kos.views.Game
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime

class WowEntitySynchronizer(
    private val dataCacheRepository: DataCacheRepository,
    private val raiderIoClient: RaiderIoClient,
    private val wowSeasonRepository: WowSeasonRepository,
) : EntitySynchronizer, WithLogger("WowEntitySynchronizer") {

    override val game: Game = Game.WOW
    override val json: Json = Json {
        serializersModule = SerializersModule {
            polymorphic(Data::class) {
                subclass(RaiderIoData::class, RaiderIoData.serializer())
            }
        }
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun synchronize(entities: List<Entity>): List<ServiceError> =
        coroutineScope {
            val runDetailsCache = DynamicCache<Either<ServiceError, RunDetails>>()

            entities as List<WowEntity>

            val syncResult = either {
                val currentSeasonSlug = wowSeasonRepository.getCurrentSeason()?.slug
                    ?: raise(SyncProcessingError("raiderIoGetCurrentSeason", "No current season found"))

                val cutoff = execute("raiderIoCutoff") {
                    raiderIoClient.cutoff()
                }.bind()

                val (profileErrors, profiles) = entities.parMap { entity ->
                    execute("raiderIoGet") {
                        raiderIoClient.get(entity).map { Pair(entity.id, it) }
                    }
                }.split()

                val results = profiles.parMap { (entityId, raiderIoResponse) ->
                    val quantile =
                        BigDecimal(raiderIoResponse.profile.mythicPlusRanks.overall.region.toDouble() / cutoff.totalPopulation * 100)
                            .setScale(2, RoundingMode.HALF_EVEN)
                            .toDouble()
                    val (runErrors, enrichedRuns) = fetchRunDetails(
                        raiderIoResponse,
                        currentSeasonSlug,
                        runDetailsCache
                    )
                    Pair(
                        runErrors,
                        DataCache(
                            entityId,
                            json.encodeToString<Data>(
                                raiderIoResponse.profile.toRaiderIoData(
                                    entityId,
                                    quantile,
                                    raiderIoResponse.specs,
                                    enrichedRuns
                                )
                            ),
                            OffsetDateTime.now(),
                            Game.WOW
                        )
                    )
                }

                val data = results.map { it.second }
                dataCacheRepository.insert(data)
                data.forEach { logger.info("Cached entity ${it.entityId}") }

                profileErrors + results.flatMap { it.first }
            }

            syncResult.fold({ listOf(it) }, { it })
        }

    private suspend fun fetchRunDetails(
        response: RaiderIoResponse,
        currentSeasonSlug: String,
        runDetailsCache: DynamicCache<Either<ServiceError, RunDetails>>
    ): Pair<List<ServiceError>, List<EnrichedMythicPlusRun>> {
        val errors = mutableListOf<ServiceError>()
        val runs = response.profile.mythicPlusBestRuns.map { run ->
            runDetailsCache.get(run.runId.toString()) {
                execute("raiderIoGetRunDetails") {
                    raiderIoClient.getRunDetails(currentSeasonSlug, run.runId.toString())
                }
            }.fold(
                ifLeft = { error -> errors.add(error); EnrichedMythicPlusRun(run, null) },
                ifRight = { details -> EnrichedMythicPlusRun(run, details) }
            )
        }
        return Pair(errors, runs)
    }

    private suspend fun <A> execute(
        operation: String,
        block: suspend () -> Either<ClientError, A>
    ): Either<ServiceError, A> =
        block().mapLeft { it.toSyncProcessingError(operation) }
}
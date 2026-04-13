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

            val currentSeasonSlug = wowSeasonRepository.getCurrentSeason()?.slug?.takeIf { it.isNotBlank() }
            if (currentSeasonSlug == null) {
                logger.warn("No current season found — cutoff and quantile will be skipped for this sync")
            }

            val syncResult = either {

                val cutoff = currentSeasonSlug?.let {
                    executeClientCall("raiderIoCutoff") { raiderIoClient.cutoff(it) }.bind()
                }

                val (profileErrors, profiles) = entities.parMap { entity ->
                    executeClientCall("raiderIoGet") {
                        raiderIoClient.get(entity).map { Pair(entity.id, it) }
                    }
                }.split()

                val data = profiles.parMap { (entityId, raiderIoResponse) ->
                    val quantile = getQuantile(cutoff, raiderIoResponse)
                    val enrichedRuns =
                        fetchRunDetails(raiderIoResponse.profile.mythicPlusBestRuns, currentSeasonSlug, runDetailsCache)

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
                }

                dataCacheRepository.insert(data)
                data.forEach { logger.info("Cached entity ${it.entityId}") }

                profileErrors
            }

            syncResult.fold({ listOf(it) }, { it })
        }

    private fun getQuantile(
        cutoff: RaiderIoCutoff?,
        raiderIoResponse: RaiderIoResponse
    ): Double? = cutoff?.let {
        BigDecimal(raiderIoResponse.profile.mythicPlusRanks.overall.region.toDouble() / it.totalPopulation * 100)
            .setScale(2, RoundingMode.HALF_EVEN)
            .toDouble()
    }

    private suspend fun fetchRunDetails(
        runs: List<MythicPlusRun>,
        currentSeasonSlug: String?,
        runDetailsCache: DynamicCache<Either<ServiceError, RunDetails>>
    ): List<EnrichedMythicPlusRun> {
        if (currentSeasonSlug == null) {
            return runs.map { EnrichedMythicPlusRun(it, null) }
        }
        return runs.map { run ->
            runDetailsCache.get(run.runId.toString()) {
                executeClientCall("raiderIoGetRunDetails") {
                    raiderIoClient.getRunDetails(currentSeasonSlug, run.runId.toString())
                }
            }.fold(
                ifLeft = { error ->
                    logger.warn("Failed to fetch run details for runId=${run.runId}: ${error.error()}")
                    EnrichedMythicPlusRun(run, null)
                },
                ifRight = { details -> EnrichedMythicPlusRun(run, details) }
            )
        }
    }

    private suspend fun <A> executeClientCall(
        operation: String,
        block: suspend () -> Either<ClientError, A>
    ): Either<ServiceError, A> =
        block().mapLeft { it.toSyncProcessingError(operation) }
}
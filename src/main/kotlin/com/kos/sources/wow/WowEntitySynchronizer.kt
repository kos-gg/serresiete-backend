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
                    val newestDataCacheEntry: RaiderIoData? = getNewestDataCacheEntry(entityId)
                    val quantile = getQuantile(cutoff, raiderIoResponse)
                    val enrichedRuns = fetchRunDetails(
                        raiderIoResponse.profile.mythicPlusBestRuns,
                        currentSeasonSlug,
                        runDetailsCache,
                        newestDataCacheEntry
                    )

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

    private suspend fun getNewestDataCacheEntry(entityId: Long): RaiderIoData? =
        dataCacheRepository.get(entityId)
            .maxByOrNull { it.inserted }
            ?.let {
                try {
                    json.decodeFromString<RaiderIoData>(it.data)
                } catch (e: Throwable) {
                    logger.debug(
                        "Couldn't deserialize entity $entityId " +
                                "while trying to obtain newest cached record.\n${e.message}"
                    )
                    null
                }
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
        responseRuns: List<MythicPlusRun>,
        currentSeasonSlug: String?,
        runDetailsCache: DynamicCache<Either<ServiceError, RunDetails>>,
        newestDataCacheEntry: RaiderIoData?
    ): List<EnrichedMythicPlusRun> {
        if (currentSeasonSlug == null) {
            return responseRuns.map { EnrichedMythicPlusRun(it, null) }
        }

        val responseRunIds = responseRuns.map { it.runId }.toSet()
        val cachedRunIds = newestDataCacheEntry?.mythicPlusBestRuns
            ?.map { it.run.runId }?.toSet().orEmpty()

        val uncachedRuns = responseRuns.filterNot { it.runId in cachedRunIds }

        val fetchedRunDetails: Map<Long, RunDetails?> = uncachedRuns.associate { run ->
            run.runId to runDetailsCache.get(run.runId.toString()) {
                executeClientCall("raiderIoGetRunDetails") {
                    raiderIoClient.getRunDetails(currentSeasonSlug, run.runId.toString())
                }
            }.fold(
                ifLeft = { error ->
                    logger.warn("Failed to fetch run details for runId=${run.runId}: ${error.error()}")
                    null
                },
                ifRight = { it }
            )
        }

        val cachedRunDetails: Map<Long, RunDetails?> = newestDataCacheEntry?.mythicPlusBestRuns
            ?.filter { it.run.runId in responseRunIds }
            ?.associate { it.run.runId to it.details }
            .orEmpty()

        logger.debug("Not requesting ${cachedRunDetails.size} run details because they are already cached");

        return responseRuns.map { run ->
            EnrichedMythicPlusRun(run, fetchedRunDetails[run.runId] ?: cachedRunDetails[run.runId])
        }
    }

    private suspend fun <A> executeClientCall(
        operation: String,
        block: suspend () -> Either<ClientError, A>
    ): Either<ServiceError, A> =
        block().mapLeft { it.toSyncProcessingError(operation) }
}

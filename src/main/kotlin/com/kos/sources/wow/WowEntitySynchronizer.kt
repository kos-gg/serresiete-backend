package com.kos.sources.wow

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.fx.coroutines.parMapUnordered
import com.kos.clients.ClientError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.common.WithLogger
import com.kos.datacache.DataCache
import com.kos.datacache.domain.*
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.PartialFailure
import com.kos.entities.ThirdPartyCallFailed
import com.kos.entities.domain.Entity
import com.kos.entities.domain.WowEntity
import com.kos.entities.sync.EntitySynchronizer
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonRepository
import com.kos.views.Game
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.OffsetDateTime

@OptIn(FlowPreview::class)
class WowEntitySynchronizer(
    private val dataCacheRepository: DataCacheRepository,
    private val raiderIoClient: RaiderIoClient,
    private val blizzardClient: BlizzardClient,
    private val wowSeasonRepository: WowSeasonRepository,
    private val concurrency: Int = 10,
) : EntitySynchronizer, WithLogger("WowEntitySynchronizer") {

    override val game: Game = Game.WOW
    override val json: Json = Json {
        serializersModule = SerializersModule {
            polymorphic(Data::class) {
                subclass(WowData::class, WowData.serializer())
            }
        }
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    override suspend fun synchronize(entities: List<Entity>): List<PartialFailure> =
        coroutineScope {
            val wowEntities = entities.filterIsInstance<WowEntity>()

            val dataChannel = Channel<DataCache>()
            val errorsChannel = Channel<PartialFailure>()
            val errorsList = mutableListOf<PartialFailure>()

            val errorsCollector = launch {
                errorsChannel.consumeAsFlow().collect { error ->
                    logger.error(error.toString())
                    errorsList.add(error)
                }
            }

            val dataCollector = launch {
                dataChannel.consumeAsFlow()
                    .buffer(50)
                    .collect { data ->
                        dataCacheRepository.insert(listOf(data))
                        logger.info("Cached entity ${data.entityId}")
                    }
            }

            val currentSeason = wowSeasonRepository.getCurrentSeason()
            val currentSeasonSlug = currentSeason?.slug?.takeIf { it.isNotBlank() }
            if (currentSeasonSlug == null) {
                logger.warn("No current season found — cutoff and quantile will be skipped for this sync")
            }

            val cutoff = currentSeasonSlug?.let {
                raiderIoClient.cutoff(it)
                    .onLeft { logger.warn("Failed to fetch cutoff, quantile will be null for this sync: $it") }
                    .getOrNull()
            }

            val start = OffsetDateTime.now()
            wowEntities.asFlow()
                .parMapUnordered(concurrency = concurrency) { entity ->
                    synchronizeWowEntity(entity, currentSeason?.id, cutoff).mapLeft { ThirdPartyCallFailed(entity.id, it) }
                }
                .collect { result ->
                    result.fold(
                        ifLeft = { errorsChannel.send(it) },
                        ifRight = { dataChannel.send(it) }
                    )
                }

            errorsChannel.close()
            dataChannel.close()

            errorsCollector.join()
            dataCollector.join()

            logger.info(
                "Finished caching Wow entities: ${wowEntities.size - errorsList.size} cached, " +
                        "${errorsList.size} failed in ${Duration.between(start, OffsetDateTime.now()).toSeconds()}s"
            )

            errorsList
        }


    private suspend fun synchronizeWowEntity(
        entity: WowEntity,
        seasonId: Int?,
        cutoff: SeasonCutoff?
    ): Either<ClientError, DataCache> =
        either {

            val newestDataCacheEntry: WowData? = getNewestDataCacheEntry(entity.id)
            val profile = raiderIoClient.get(entity).bind()
            val quantile = getQuantile(cutoff, profile)
            val enrichedRuns = fetchRunDetails(entity, profile.bestRuns, seasonId, newestDataCacheEntry)

            DataCache(
                entity.id,
                json.encodeToString<Data>(
                    profile.toWowData(
                        entity.id,
                        quantile,
                        enrichedRuns
                    )
                ),
                OffsetDateTime.now(),
                Game.WOW
            )
        }

    private suspend fun getNewestDataCacheEntry(entityId: Long): WowData? =
        dataCacheRepository.get(entityId)
            .maxByOrNull { it.inserted }
            ?.let {
                try {
                    json.decodeFromString<WowData>(it.data)
                } catch (e: SerializationException) {
                    logger.warn(
                        "Couldn't deserialize entity $entityId " +
                                "while trying to obtain newest cached record.\n${e.message}"
                    )
                    null
                }
            }

    private fun getQuantile(
        cutoff: SeasonCutoff?,
        profile: WowCharacterProfile
    ): Double? = cutoff?.let {
        BigDecimal(profile.overallRank.region.toDouble() / it.totalPopulation * 100)
            .setScale(2, RoundingMode.HALF_EVEN)
            .toDouble()
    }

    private suspend fun fetchRunDetails(
        entity: WowEntity,
        responseRuns: List<MythicPlusRun>,
        seasonId: Int?,
        newestDataCacheEntry: WowData?
    ): List<EnrichedMythicPlusRun> {
        val cachedRunDetails: Map<Long, RunDetails> =
            newestDataCacheEntry?.mythicPlusBestRuns
                ?.mapNotNull { cached -> cached.details?.let { cached.run.runId to it } }
                ?.toMap()
                .orEmpty()

        val uncachedRuns = responseRuns.filterNot { it.runId in cachedRunDetails }

        val mythicPlusBestRuns =
            if (seasonId == null || uncachedRuns.isEmpty()) {
                emptyList()
            } else {
                logger.debug("Fetching best runs details for ${entity.id} since ${uncachedRuns.size} are not cached")
                blizzardClient.getMythicKeystoneProfile(entity.region, entity.realm, entity.name, seasonId)
                    .map { it.bestRuns }
                    .getOrElse { error ->
                        logger.debug("Failed to fetch Blizzard best runs for entity ${entity.id}: $error")
                        emptyList()
                    }
            }

        val fetchedRunDetails: Map<Long, RunDetails?> = uncachedRuns.associate { run ->
            run.runId to mythicPlusBestRuns
                .find { it.dungeonId == run.dungeonId && it.keyLevel == run.keyLevel && it.timed == (run.upgrades > 0) }
                ?.details
        }

        return responseRuns.map { run ->
            EnrichedMythicPlusRun(run, cachedRunDetails[run.runId] ?: fetchedRunDetails[run.runId])
        }
    }

}

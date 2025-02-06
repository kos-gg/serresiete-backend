package com.kos.datacache

import arrow.core.Either
import arrow.core.raise.either
import com.kos.entities.Entity
import com.kos.entities.LolEntity
import com.kos.entities.WowEntity
import com.kos.entities.repository.EntitiesRepository
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.*
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.common.*
import com.kos.common.Retry.retryEitherWithFixedDelay
import com.kos.datacache.repository.DataCacheRepository
import com.kos.views.Game
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

data class DataCacheService(
    private val dataCacheRepository: DataCacheRepository,
    private val entitiesRepository: EntitiesRepository,
    private val raiderIoClient: RaiderIoClient,
    private val riotClient: RiotClient,
    private val blizzardClient: BlizzardClient,
    private val retryConfig: RetryConfig
) : WithLogger("DataCacheService") {

    private val ttl: Long = 24
    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(Data::class) {
                subclass(RaiderIoData::class, RaiderIoData.serializer())
                subclass(RiotData::class, RiotData.serializer())
                subclass(HardcoreData::class, HardcoreData.serializer())
            }
        }
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    suspend fun get(entityId: Long) = dataCacheRepository.get(entityId)
    suspend fun getData(entitiesIds: List<Long>, oldFirst: Boolean): Either<JsonParseError, List<Data>> =
        either {
            val comparator: (List<DataCache>) -> DataCache? = if (oldFirst) {
                { it.minByOrNull { dc -> dc.inserted } }
            } else {
                { it.maxByOrNull { dc -> dc.inserted } }
            }

            entitiesIds.mapNotNull { id ->
                comparator(get(id))?.let { dataCache ->
                    try {
                        json.decodeFromString<Data>(dataCache.data)
                    } catch (se: SerializationException) {
                        raise(JsonParseError(dataCache.data, "", se.stackTraceToString()))
                    } catch (iae: IllegalArgumentException) {
                        raise(JsonParseError(dataCache.data, "", iae.stackTraceToString()))
                    }
                }
            }
        }

    @Suppress("UNCHECKED_CAST")
    suspend fun cache(entities: List<Entity>, game: Game): List<HttpError> {
        return when (game) {
            Game.WOW -> cacheWowEntities(entities as List<WowEntity>)
            Game.LOL -> cacheLolEntities(entities as List<LolEntity>)
            Game.WOW_HC -> cacheWowHardcoreEntities(entities as List<WowEntity>)
        }
    }

    private suspend fun cacheLolEntities(lolEntities: List<LolEntity>): List<HttpError> = coroutineScope {
        val errorsChannel = Channel<HttpError>()
        val dataChannel = Channel<DataCache>()
        val errorsList = mutableListOf<HttpError>()
        val matchCache = DynamicCache<Either<HttpError, GetMatchResponse>>()

        val errorsCollector = launch {
            errorsChannel.consumeAsFlow().collect { error ->
                logger.error(error.error())
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

        val start = OffsetDateTime.now()
        lolEntities.asFlow()
            .buffer(10)
            .collect { lolEntity ->
                val result = cachedLolEntity(lolEntity, matchCache)
                result.fold(
                    ifLeft = { error -> errorsChannel.send(error) },
                    ifRight = { (id, riotData) ->
                        dataChannel.send(
                            DataCache(
                                id,
                                json.encodeToString<Data>(riotData),
                                OffsetDateTime.now(),
                                Game.LOL
                            )
                        )
                    }
                )
            }

        dataChannel.close()
        errorsChannel.close()

        errorsCollector.join()
        dataCollector.join()

        logger.info("Finished Caching Lol entities")
        logger.debug(
            "cached ${lolEntities.size} entities in ${
                Duration.between(start, OffsetDateTime.now()).toSeconds() / 60.0
            } minutes"
        )
        logger.debug("dynamic match cache hit rate: ${matchCache.hitRate}%")
        errorsList
    }

    private suspend fun cachedLolEntity(
        lolEntity: LolEntity,
        matchCache: DynamicCache<Either<HttpError, GetMatchResponse>>
    ): Either<HttpError, Pair<Long, RiotData>> =
        either {

            val newestDataCacheEntry: RiotData? =
                dataCacheRepository.get(lolEntity.id).maxByOrNull { it.inserted }?.let {
                    try {
                        json.decodeFromString<RiotData>(it.data)
                    } catch (e: Throwable) {
                        logger.debug("Couldn't deserialize entity ${lolEntity.id} " +
                                "while trying to obtain newest cached record.\n${e.message}")
                        null
                    }
                }

            val leagues: List<LeagueEntryResponse> =
                retryEitherWithFixedDelay(retryConfig, "getLeagueEntriesBySummonerId") {
                    riotClient.getLeagueEntriesBySummonerId(lolEntity.summonerId)
                }.bind()

            val leagueWithMatches: List<LeagueMatchData> =
                coroutineScope {
                    leagues.map { leagueEntry ->
                        async {
                            val lastMatchesForLeague: List<String> =
                                retryEitherWithFixedDelay(retryConfig, "getMatchesByPuuid") {
                                    riotClient.getMatchesByPuuid(lolEntity.puuid, leagueEntry.queueType.toInt())
                                }.bind()

                            val matchesToRequest = newestDataCacheEntry._fold(
                                left = { lastMatchesForLeague },
                                right = { record ->
                                    lastMatchesForLeague
                                        .filterNot { id ->
                                            record.leagues[leagueEntry.queueType]?.matches?.map { it.id }
                                                ?.contains(id)._fold({ false }, { it })
                                        }
                                }
                            )

                            val matchResponses: List<GetMatchResponse> = matchesToRequest.map { matchId ->
                                matchCache.get(matchId) {
                                    retryEitherWithFixedDelay(retryConfig, "getMatchById") {
                                        riotClient.getMatchById(matchId)
                                    }
                                }.bind()
                            }

                            LeagueMatchData(
                                leagueEntry,
                                matchResponses,
                                newestDataCacheEntry?.leagues?.get(leagueEntry.queueType)?.matches.orEmpty()
                                    .filter { lastMatchesForLeague.contains(it.id) }
                            )
                        }
                    }.awaitAll()
                }

            Pair(lolEntity.id, RiotData.apply(lolEntity, leagueWithMatches))
        }


    private suspend fun cacheWowEntities(wowEntities: List<WowEntity>): List<HttpError> =
        coroutineScope {
            val cutoffErrorOrMaybeErrors = either {
                val cutoff = raiderIoClient.cutoff().bind()
                val errorsAndData =
                    wowEntities.map {
                        async {
                            retryEitherWithFixedDelay(retryConfig, "raiderIoGet") {
                                raiderIoClient.get(it).map { r -> Pair(it.id, r) }
                            }
                        }
                    }
                        .awaitAll()
                        .split()
                val data = errorsAndData.second.map {
                    DataCache(
                        it.first,
                        json.encodeToString<Data>(
                            it.second.profile.toRaiderIoData(
                                it.first,
                                BigDecimal(it.second.profile.mythicPlusRanks.overall.region.toDouble() / cutoff.totalPopulation * 100).setScale(
                                    2,
                                    RoundingMode.HALF_EVEN
                                ).toDouble(),
                                it.second.specs
                            )
                        ),
                        OffsetDateTime.now(),
                        Game.WOW
                    )
                }
                dataCacheRepository.insert(data)
                data.forEach { logger.info("Cached entity ${it.entityId}") }
                errorsAndData.first
            }
            cutoffErrorOrMaybeErrors.mapLeft { listOf(it) }.fold({ it }, { it })
        }

    private suspend fun cacheWowHardcoreEntities(wowEntities: List<WowEntity>): List<HttpError> =
        coroutineScope {
            val errorsAndData =
                wowEntities.map { wowEntity ->
                    async {
                        either {
                            val newestDataCacheEntry: HardcoreData? =
                                dataCacheRepository.get(wowEntity.id).maxByOrNull { it.inserted }?.let {
                                    try {
                                        json.decodeFromString<HardcoreData>(it.data)
                                    } catch (e: Throwable) {
                                        logger.debug("Couldn't deserialize entity ${wowEntity.id} " +
                                                "while trying to obtain newest cached record.\n${e.message}")
                                        null
                                    }
                                }

                            if (newestDataCacheEntry?.isDead != true) {
                                syncWowHardcoreEntity(wowEntity, newestDataCacheEntry).bind()
                            } else {
                                raise(WowHardcoreCharacterIsDead(wowEntity.name, wowEntity.id))
                            }
                        }
                    }
                }.awaitAll().split()

            val data = errorsAndData.second.map {
                DataCache(
                    it.first,
                    json.encodeToString<Data>(it.second),
                    OffsetDateTime.now(),
                    Game.WOW_HC
                )
            }

            dataCacheRepository.insert(data)

            errorsAndData.first
        }

    private suspend fun syncWowHardcoreEntity(
        wowEntity: WowEntity,
        newestDataCacheEntry: HardcoreData?
    ): Either<HttpError, Pair<Long, HardcoreData>> {
        return either {
            val characterResponse: GetWowCharacterResponse =
                retryEitherWithFixedDelay(retryConfig, "blizzardGetCharacter") {
                    blizzardClient.getCharacterProfile(
                        wowEntity.region,
                        wowEntity.realm,
                        wowEntity.name
                    )
                }.onLeft { error ->
                    when (error) {
                        is NotFoundHardcoreCharacter -> {
                            handleNotFoundHardcoreCharacter(
                                newestDataCacheEntry,
                                wowEntity
                            )
                        }
                    }
                }.bind()

            val mediaResponse =
                retryEitherWithFixedDelay(retryConfig, "blizzardGetCharacterMedia") {
                    blizzardClient.getCharacterMedia(
                        wowEntity.region,
                        wowEntity.realm,
                        wowEntity.name
                    )
                }.bind()

            val equipmentResponse =
                retryEitherWithFixedDelay(retryConfig, "blizzardGetCharacterEquipment") {
                    blizzardClient.getCharacterEquipment(
                        wowEntity.region,
                        wowEntity.realm,
                        wowEntity.name
                    )
                }.bind()

            val existentItemsAndItemsToRequest: Pair<List<WowItem>, List<WowEquippedItemResponse>> =
                newestDataCacheEntry._fold(
                    left = { equipmentResponse.equippedItems.map { Either.Right(it) } },
                    right = { record ->
                        equipmentResponse.equippedItems.fold(emptyList<Either<WowItem, WowEquippedItemResponse>>()) { acc, itemResponse ->
                            when (val maybeItem =
                                record.items.find { itemResponse.item.id == it.id }) {
                                null -> acc + Either.Right(itemResponse)
                                else -> acc + Either.Left(maybeItem)
                            }

                        }
                    }).split()

            val newItemsWithIcons: List<Triple<WowEquippedItemResponse, GetWowItemResponse, GetWowMediaResponse?>> =
                existentItemsAndItemsToRequest.second.map {
                    either {
                        Triple(
                            it,
                            retryEitherWithFixedDelay(retryConfig, "blizzardGetItem") {
                                blizzardClient.getItem(wowEntity.region, it.item.id)
                            }.bind(),
                            retryEitherWithFixedDelay(retryConfig, "blizzardGetItemMedia") {
                                blizzardClient.getItemMedia(
                                    wowEntity.region,
                                    it.item.id,
                                )
                            }.getOrNull()
                        )
                    }
                }.bindAll()

            val stats: GetWowCharacterStatsResponse =
                retryEitherWithFixedDelay(retryConfig, "blizzardGetStats") {
                    blizzardClient.getCharacterStats(
                        wowEntity.region,
                        wowEntity.realm,
                        wowEntity.name
                    )
                }.bind()

            val specializations: GetWowSpecializationsResponse =
                retryEitherWithFixedDelay(retryConfig, "blizzardGetSpecializations") {
                    blizzardClient.getCharacterSpecializations(
                        wowEntity.region,
                        wowEntity.realm,
                        wowEntity.name
                    )
                }.bind()

            val wowHeadEmbeddedResponse: RaiderioWowHeadEmbeddedResponse? =
                retryEitherWithFixedDelay(retryConfig, "raiderioWowheadEmbedded") {
                    raiderIoClient.wowheadEmbeddedCalculator(wowEntity)
                }.getOrNull()

            wowEntity.id to HardcoreData.apply(
                wowEntity.region,
                characterResponse,
                mediaResponse,
                existentItemsAndItemsToRequest.first,
                newItemsWithIcons,
                stats,
                specializations,
                wowHeadEmbeddedResponse
            )
        }
    }

    private suspend fun handleNotFoundHardcoreCharacter(
        newestCharacterDataCacheEntry: HardcoreData?,
        wowEntity: WowEntity
    ) {
        newestCharacterDataCacheEntry.fold(
            {
                entitiesRepository.delete(wowEntity.id)
            },
            {
                dataCacheRepository.insert(
                    listOf(
                        DataCache(
                            wowEntity.id,
                            json.encodeToString<Data>(it.copy(isDead = true)),
                            OffsetDateTime.now(),
                            Game.WOW_HC
                        )
                    )
                )
            })
    }


    suspend fun clear(game: Game?, keepLastRecord: Boolean): Int =
        dataCacheRepository.deleteExpiredRecord(ttl, game, keepLastRecord)
}
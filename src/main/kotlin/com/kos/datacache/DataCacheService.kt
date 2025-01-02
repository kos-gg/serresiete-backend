package com.kos.datacache

import arrow.core.Either
import arrow.core.raise.either
import com.kos.characters.Character
import com.kos.characters.LolCharacter
import com.kos.characters.WowCharacter
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

    suspend fun get(characterId: Long) = dataCacheRepository.get(characterId)
    suspend fun getData(characterIds: List<Long>, oldFirst: Boolean): Either<JsonParseError, List<Data>> =
        either {
            val comparator: (List<DataCache>) -> DataCache? = if (oldFirst) {
                { it.minByOrNull { dc -> dc.inserted } }
            } else {
                { it.maxByOrNull { dc -> dc.inserted } }
            }

            characterIds.mapNotNull { id ->
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
    suspend fun cache(characters: List<Character>, game: Game): List<HttpError> {
        return when (game) {
            Game.WOW -> cacheWowCharacters(characters as List<WowCharacter>)
            Game.LOL -> cacheLolCharacters(characters as List<LolCharacter>)
            Game.WOW_HC -> cacheWowHardcoreCharacters(characters as List<WowCharacter>)
        }
    }

    private suspend fun cacheLolCharacters(lolCharacters: List<LolCharacter>): List<HttpError> = coroutineScope {
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
                    logger.info("Cached character ${data.characterId}")
                }
        }

        val start = OffsetDateTime.now()
        lolCharacters.asFlow()
            .buffer(10)
            .collect { lolCharacter ->
                val result = cacheLolCharacter(lolCharacter, matchCache)
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

        logger.info("Finished Caching Lol characters")
        logger.debug(
            "cached ${lolCharacters.size} characters in ${
                Duration.between(start, OffsetDateTime.now()).toSeconds() / 60.0
            } minutes"
        )
        logger.debug("dynamic match cache hit rate: ${matchCache.hitRate}%")
        errorsList
    }

    private suspend fun cacheLolCharacter(
        lolCharacter: LolCharacter,
        matchCache: DynamicCache<Either<HttpError, GetMatchResponse>>
    ): Either<HttpError, Pair<Long, RiotData>> =
        either {

            val newestCharacterDataCacheEntry: RiotData? =
                dataCacheRepository.get(lolCharacter.id).maxByOrNull { it.inserted }?.let {
                    try {
                        json.decodeFromString<RiotData>(it.data)
                    } catch (e: Throwable) {
                        logger.debug("Couldn't deserialize character ${lolCharacter.id} while trying to obtain newest cached record.\n${e.message}")
                        null
                    }
                }

            val leagues: List<LeagueEntryResponse> =
                retryEitherWithFixedDelay(retryConfig, "getLeagueEntriesBySummonerId") {
                    riotClient.getLeagueEntriesBySummonerId(lolCharacter.summonerId)
                }.bind()

            val leagueWithMatches: List<LeagueMatchData> =
                coroutineScope {
                    leagues.map { leagueEntry ->
                        async {
                            val lastMatchesForLeague: List<String> =
                                retryEitherWithFixedDelay(retryConfig, "getMatchesByPuuid") {
                                    riotClient.getMatchesByPuuid(lolCharacter.puuid, leagueEntry.queueType.toInt())
                                }.bind()

                            val matchesToRequest = newestCharacterDataCacheEntry._fold(
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
                                newestCharacterDataCacheEntry?.leagues?.get(leagueEntry.queueType)?.matches.orEmpty()
                                    .filter { lastMatchesForLeague.contains(it.id) }
                            )
                        }
                    }.awaitAll()
                }

            Pair(lolCharacter.id, RiotData.apply(lolCharacter, leagueWithMatches))
        }


    private suspend fun cacheWowCharacters(wowCharacters: List<WowCharacter>): List<HttpError> =
        coroutineScope {
            val cutoffErrorOrMaybeErrors = either {
                val cutoff = raiderIoClient.cutoff().bind()
                val errorsAndData =
                    wowCharacters.map {
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
                data.forEach { logger.info("Cached character ${it.characterId}") }
                errorsAndData.first
            }
            cutoffErrorOrMaybeErrors.mapLeft { listOf(it) }.fold({ it }, { it })
        }

    private suspend fun cacheWowHardcoreCharacters(wowCharacters: List<WowCharacter>): List<HttpError> =
        coroutineScope {
            val errorsAndData =
                wowCharacters.map { wowCharacter ->
                    async {
                        either {
                            val newestCharacterDataCacheEntry: HardcoreData? =
                                dataCacheRepository.get(wowCharacter.id).maxByOrNull { it.inserted }?.let {
                                    try {
                                        json.decodeFromString<HardcoreData>(it.data)
                                    } catch (e: Throwable) {
                                        logger.debug("Couldn't deserialize character ${wowCharacter.id} while trying to obtain newest cached record.\n${e.message}")
                                        null
                                    }
                                }
//quan doni 404 marcar com mort
                            //si el troba mirar si tenen el mateix blizzardId
                            //hauriem d'actualitzar el registre mes nou del data cache i marcarlo com a mort en cas de si no el troba
                            val characterResponse: GetWowCharacterResponse =
                                retryEitherWithFixedDelay(retryConfig, "blizzardGetCharacter") {
                                    blizzardClient.getCharacterProfile(
                                        wowCharacter.region,
                                        wowCharacter.realm,
                                        wowCharacter.name
                                    )
                                }.bind()
                            val mediaResponse = retryEitherWithFixedDelay(retryConfig, "blizzardGetCharacterMedia") {
                                blizzardClient.getCharacterMedia(
                                    wowCharacter.region,
                                    wowCharacter.realm,
                                    wowCharacter.name
                                )
                            }.bind()
                            val equipmentResponse =
                                retryEitherWithFixedDelay(retryConfig, "blizzardGetCharacterEquipment") {
                                    blizzardClient.getCharacterEquipment(
                                        wowCharacter.region,
                                        wowCharacter.realm,
                                        wowCharacter.name
                                    )
                                }.bind()

                            val existentItemsAndItemsToRequest: Pair<List<WowItem>, List<WowEquippedItemResponse>> =
                                newestCharacterDataCacheEntry._fold(
                                    left = { equipmentResponse.equippedItems.map { Either.Right(it) } },
                                    right = { record ->
                                        equipmentResponse.equippedItems.fold(emptyList<Either<WowItem, WowEquippedItemResponse>>()) { acc, itemResponse ->
                                            when (val maybeItem = record.items.find { itemResponse.item.id == it.id }) {
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
                                                blizzardClient.getItem(wowCharacter.region, it.item.id)
                                            }.bind(),
                                            retryEitherWithFixedDelay(retryConfig, "blizzardGetItemMedia") {
                                                blizzardClient.getItemMedia(
                                                    wowCharacter.region,
                                                    it.item.id,
                                                )
                                            }.getOrNull()
                                        )
                                    }
                                }.bindAll()

                            val stats: GetWowCharacterStatsResponse =
                                retryEitherWithFixedDelay(retryConfig, "blizzardGetStats") {
                                    blizzardClient.getCharacterStats(
                                        wowCharacter.region,
                                        wowCharacter.realm,
                                        wowCharacter.name
                                    )
                                }.bind()

                            val specializations: GetWowSpecializationsResponse =
                                retryEitherWithFixedDelay(retryConfig, "blizzardGetSpecializations") {
                                    blizzardClient.getCharacterSpecializations(
                                        wowCharacter.region,
                                        wowCharacter.realm,
                                        wowCharacter.name
                                    )
                                }.bind()

                            val wowHeadEmbeddedResponse: RaiderioWowHeadEmbeddedResponse? =
                                retryEitherWithFixedDelay(retryConfig, "raiderioWowheadEmbedded") {
                                    raiderIoClient.wowheadEmbeddedCalculator(wowCharacter)
                                }.getOrNull()

                            wowCharacter.id to HardcoreData.apply(
                                wowCharacter.region,
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


    suspend fun clear(): Int = dataCacheRepository.deleteExpiredRecord(ttl)
}
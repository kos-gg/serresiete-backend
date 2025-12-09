package com.kos.sources.wowhc

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.blizzard.BlizzardClient
import com.kos.sources.wowhc.staticdata.wowitems.WowItemsDatabaseRepository
import com.kos.clients.domain.Data
import com.kos.clients.domain.GetWowCharacterStatsResponse
import com.kos.clients.domain.GetWowItemResponse
import com.kos.clients.domain.GetWowMediaResponse
import com.kos.clients.domain.GetWowSpecializationsResponse
import com.kos.clients.domain.HardcoreData
import com.kos.clients.domain.RaiderioWowHeadEmbeddedResponse
import com.kos.clients.domain.WowEquippedItemResponse
import com.kos.clients.domain.WowItem
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.common.HttpError
import com.kos.common.NotFoundHardcoreCharacter
import com.kos.common.Retry
import com.kos.common.RetryConfig
import com.kos.common.UnableToSyncEntityError
import com.kos.common.WithLogger
import com.kos.common.WowHardcoreCharacterIsDead
import com.kos.common._fold
import com.kos.common.fold
import com.kos.common.split
import com.kos.datacache.DataCache
import com.kos.datacache.EntitySynchronizer
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.domain.Entity
import com.kos.entities.domain.WowEntity
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import java.time.OffsetDateTime
import kotlin.collections.plus

class WowHardcoreEntitySynchronizer(
    private val dataCacheRepository: DataCacheRepository,
    private val entitiesRepository: EntitiesRepository,
    private val raiderIoClient: RaiderIoClient,
    private val blizzardClient: BlizzardClient,
    private val wowItemsDatabaseRepository: WowItemsDatabaseRepository,
    private val retryConfig: RetryConfig,
) : EntitySynchronizer, WithLogger("WowHardcoreEntitySynchronizer") {

    override val game: Game = Game.WOW_HC
    override val json: Json = Json {
        serializersModule = SerializersModule {
            polymorphic(Data::class) {
                subclass(HardcoreData::class, HardcoreData.serializer())
            }
        }
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun synchronize(entities: List<Entity>): List<HttpError> =
        coroutineScope {
            val wowHardcoreEntities = entities as List<WowEntity>

            val errorsAndData: Pair<List<HttpError>, List<Pair<Long, HardcoreData>>> =
                wowHardcoreEntities.map { wowEntity ->
                    async {
                        either {
                            val newestDataCacheEntry: HardcoreData? =
                                dataCacheRepository.get(wowEntity.id).maxByOrNull {
                                    it.inserted
                                }?.let {
                                    try {
                                        json.decodeFromString<HardcoreData>(it.data)
                                    } catch (e: Throwable) {
                                        logger.debug(
                                            "Couldn't deserialize entity ${wowEntity.id} while trying to obtain newest cached record.\n${e.message}"
                                        )
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
            Retry.retryEitherWithFixedDelay(retryConfig, "blizzardGetCharacter") {
                blizzardClient.getCharacterProfile(
                    wowEntity.region,
                    wowEntity.realm,
                    wowEntity.name
                )
            }.fold(
                ifLeft = { error ->
                    when (error) {
                        is NotFoundHardcoreCharacter -> {
                            handleNotFoundHardcoreCharacter(newestDataCacheEntry, wowEntity)
                        }

                        else -> Either.Left(error)
                    }.bind()
                },
                ifRight = { response ->
                    if (newestDataCacheEntry != null && wowEntity.blizzardId != response.id) {
                        markWowHardcoreCharacterAsDead(wowEntity, newestDataCacheEntry)
                    } else {
                        val mediaResponse =
                            Retry.retryEitherWithFixedDelay(retryConfig, "blizzardGetCharacterMedia") {
                                blizzardClient.getCharacterMedia(
                                    wowEntity.region,
                                    wowEntity.realm,
                                    wowEntity.name
                                )
                            }.bind()

                        val equipmentResponse =
                            Retry.retryEitherWithFixedDelay(retryConfig, "blizzardGetCharacterEquipment") {
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

                        //TODO: BRING BACK RETRY WHEN IT PERFORMS BETTER.
                        val newItemsWithIcons: List<Triple<WowEquippedItemResponse, GetWowItemResponse, GetWowMediaResponse?>> =
                            existentItemsAndItemsToRequest.second.map { x ->
                                either {
                                    Triple(
                                        x,

                                        blizzardClient.getItem(wowEntity.region, x.item.id).fold(
                                            ifLeft = {
                                                wowItemsDatabaseRepository.getItem(x.item.id)
                                            },
                                            ifRight = { Either.Right(it) }
                                        ).bind(),
                                        blizzardClient.getItemMedia(
                                            wowEntity.region,
                                            x.item.id,
                                        ).fold(ifLeft = {
                                            wowItemsDatabaseRepository.getItemMedia(x.item.id)
                                        }, ifRight = { Either.Right(it) }).getOrNull()
                                    )
                                }
                            }.bindAll()

                        val stats: GetWowCharacterStatsResponse =
                            Retry.retryEitherWithFixedDelay(retryConfig, "blizzardGetStats") {
                                blizzardClient.getCharacterStats(
                                    wowEntity.region,
                                    wowEntity.realm,
                                    wowEntity.name
                                )
                            }.bind()

                        val specializations: GetWowSpecializationsResponse =
                            Retry.retryEitherWithFixedDelay(retryConfig, "blizzardGetSpecializations") {
                                blizzardClient.getCharacterSpecializations(
                                    wowEntity.region,
                                    wowEntity.realm,
                                    wowEntity.name
                                )
                            }.bind()

                        val wowHeadEmbeddedResponse: RaiderioWowHeadEmbeddedResponse? =
                            Retry.retryEitherWithFixedDelay(retryConfig, "raiderioWowheadEmbedded") {
                                raiderIoClient.wowheadEmbeddedCalculator(wowEntity)
                            }.getOrNull()

                        wowEntity.id to HardcoreData.Companion.apply(
                            wowEntity.region,
                            response,
                            mediaResponse,
                            existentItemsAndItemsToRequest.first,
                            newItemsWithIcons,
                            stats,
                            specializations,
                            wowHeadEmbeddedResponse
                        )
                    }
                })
        }
    }

    private suspend fun handleNotFoundHardcoreCharacter(
        newestCharacterDataCacheEntry: HardcoreData?,
        wowEntity: WowEntity
    ): Either<HttpError, Pair<Long, HardcoreData>> {
        return newestCharacterDataCacheEntry.fold(
            {
                entitiesRepository.delete(wowEntity.id)
                //TODO: New exception to handle this scenario (this one is placeholder for the moment)
                Either.Left(UnableToSyncEntityError(wowEntity.id, Game.WOW_HC))
            },
            {
                Either.Right(markWowHardcoreCharacterAsDead(wowEntity, it))
            })
    }

    private fun markWowHardcoreCharacterAsDead(
        wowEntity: WowEntity,
        newestCharacterDataCacheEntry: HardcoreData
    ): Pair<Long, HardcoreData> {
        return wowEntity.id to newestCharacterDataCacheEntry.copy(isDead = true)
    }

}
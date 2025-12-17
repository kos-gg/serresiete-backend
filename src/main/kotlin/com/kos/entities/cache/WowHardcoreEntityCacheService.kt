package com.kos.entities.cache

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.HttpError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.blizzard.BlizzardDatabaseClient
import com.kos.clients.domain.*
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.toServiceError
import com.kos.common.Retry.retryEitherWithFixedDelay
import com.kos.common.RetryConfig
import com.kos.common._fold
import com.kos.common.error.ServiceError
import com.kos.common.error.SyncProcessingError
import com.kos.common.error.WowHardcoreCharacterIsDead
import com.kos.common.fold
import com.kos.common.split
import com.kos.datacache.DataCache
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.Entity
import com.kos.entities.WowEntity
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import java.time.OffsetDateTime

class WowHardcoreEntityCacheService(
    dataCacheRepository: DataCacheRepository,
    private val entitiesRepository: EntitiesRepository,
    private val raiderIoClient: RaiderIoClient,
    private val blizzardClient: BlizzardClient,
    private val blizzardDatabaseClient: BlizzardDatabaseClient,
    private val retryConfig: RetryConfig,
) : EntityCacheService(
    dataCacheRepository
) {

    override val game: Game = Game.WOW_HC

    @Suppress("UNCHECKED_CAST")
    override suspend fun cache(entities: List<Entity>): List<ServiceError> =
        coroutineScope {
            entities as List<WowEntity>

            val errorsAndData: Pair<List<ServiceError>, List<Pair<Long, HardcoreData>>> =
                entities.map { wowEntity ->
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
                                syncWowHardcoreEntityV2(wowEntity, newestDataCacheEntry).bind()
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


    private suspend fun syncWowHardcoreEntityV2(
        wowEntity: WowEntity,
        newestDataCacheEntry: HardcoreData?
    ): Either<ServiceError, Pair<Long, HardcoreData>> {
        return either {
            retryEitherWithFixedDelay(retryConfig, "blizzardGetCharacter") {
                blizzardClient.getCharacterProfile(
                    wowEntity.region,
                    wowEntity.realm,
                    wowEntity.name
                )
            }.fold(
                ifLeft = { error ->
                    when {
                        error is HttpError && error.status == 404 ->
                            handleNotFoundHardcoreCharacterV2(newestDataCacheEntry, wowEntity)

                        else ->
                            Either.Left(error.toServiceError("getCharacterProfileV2"))
                    }.bind()
                },
                ifRight = { response ->
                    if (newestDataCacheEntry != null && wowEntity.blizzardId != response.id) {
                        markWowHardcoreCharacterAsDead(wowEntity, newestDataCacheEntry)
                    } else {

                        val mediaResponse = execute("getCharacterMediaV2") {
                            retryEitherWithFixedDelay(retryConfig, "blizzardGetCharacterMedia") {
                                blizzardClient.getCharacterMedia(
                                    wowEntity.region,
                                    wowEntity.realm,
                                    wowEntity.name
                                )
                            }
                        }.bind()

                        val equipmentResponse = execute("getCharacterEquipmentV2") {
                            retryEitherWithFixedDelay(retryConfig, "blizzardGetCharacterEquipment") {
                                blizzardClient.getCharacterEquipment(
                                    wowEntity.region,
                                    wowEntity.realm,
                                    wowEntity.name
                                )
                            }
                        }.bind()

                        val stats = execute("getCharacterStatsV2") {
                            retryEitherWithFixedDelay(retryConfig, "blizzardGetStats") {
                                blizzardClient.getCharacterStats(
                                    wowEntity.region,
                                    wowEntity.realm,
                                    wowEntity.name
                                )
                            }
                        }.bind()

                        val specializations = execute("getCharacterSpecializationsV2") {
                            retryEitherWithFixedDelay(retryConfig, "blizzardGetSpecializations") {
                                blizzardClient.getCharacterSpecializations(
                                    wowEntity.region,
                                    wowEntity.realm,
                                    wowEntity.name
                                )
                            }
                        }.bind()

                        val wowHeadEmbeddedResponse = execute("wowheadEmbeddedCalculatorV2") {
                            retryEitherWithFixedDelay(retryConfig, "raiderioWowheadEmbedded") {
                                raiderIoClient.wowheadEmbeddedCalculator(wowEntity)
                            }
                        }.getOrNull()

                        val existentItemsAndItemsToRequest =
                            getExistentItemsAndItemsToRequest(newestDataCacheEntry, equipmentResponse)

                        //TODO: BRING BACK RETRY WHEN IT PERFORMS BETTER.
                        val newItemsWithIcons =
                            getNewItemsWithIcons(existentItemsAndItemsToRequest, wowEntity).bindAll()

                        wowEntity.id to HardcoreData.apply(
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

    private suspend fun getNewItemsWithIcons(
        existentItemsAndItemsToRequest: Pair<List<WowItem>, List<WowEquippedItemResponse>>,
        wowEntity: WowEntity
    ): List<Either<ServiceError, Triple<WowEquippedItemResponse, GetWowItemResponse, GetWowMediaResponse?>>> =
        existentItemsAndItemsToRequest.second.map { equippedItem ->
            either {
                val item = blizzardClient.getItem(wowEntity.region, equippedItem.item.id).fold(
                    ifLeft = {
                        execute("getItemV2") {
                            blizzardDatabaseClient.getItem(equippedItem.item.id)
                        }
                    },
                    ifRight = { Either.Right(it) }
                ).bind()

                val itemMedia = blizzardClient.getItemMedia(
                    wowEntity.region,
                    equippedItem.item.id,
                ).fold(ifLeft = {
                    execute("getItemMediaV2") {
                        blizzardDatabaseClient.getItemMedia(equippedItem.item.id)
                    }
                }, ifRight = { Either.Right(it) }).getOrNull()

                Triple(equippedItem, item, itemMedia)
            }
        }

    private fun getExistentItemsAndItemsToRequest(
        newestDataCacheEntry: HardcoreData?,
        equipmentResponse: GetWowEquipmentResponse
    ) = newestDataCacheEntry._fold(
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

    private suspend fun handleNotFoundHardcoreCharacterV2(
        newestCharacterDataCacheEntry: HardcoreData?,
        wowEntity: WowEntity
    ): Either<ServiceError, Pair<Long, HardcoreData>> {
        return newestCharacterDataCacheEntry.fold(
            {
                entitiesRepository.delete(wowEntity.id)

                Either.Left(
                    SyncProcessingError(
                        "WOW_HARDCORE",
                        "Unable to sync character because no recent data was found in cache."
                    )
                )
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
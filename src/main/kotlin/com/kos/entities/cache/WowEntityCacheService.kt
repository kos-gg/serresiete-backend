package com.kos.entities.cache

import arrow.core.raise.either
import com.kos.clients.domain.Data
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.common.Retry.retryEitherWithFixedDelay
import com.kos.common.RetryConfig
import com.kos.common.error.ServiceError
import com.kos.common.split
import com.kos.datacache.DataCache
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.Entity
import com.kos.entities.WowEntity
import com.kos.views.Game
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime

class WowEntityCacheService(
    dataCacheRepository: DataCacheRepository,
    private val raiderIoClient: RaiderIoClient,
    private val retryConfig: RetryConfig
) : EntityCacheService(dataCacheRepository) {

    override val game: Game = Game.WOW

    @Suppress("UNCHECKED_CAST")
    override suspend fun cache(entities: List<Entity>): List<ServiceError> =
        coroutineScope {
            entities as List<WowEntity>

            val cutoffErrorOrMaybeErrors = either {
                val cutoff = execute("raiderIoCutoff") {
                    raiderIoClient.cutoff()
                }.bind()
                val errorsAndData =
                    entities.map {
                        async {
                            execute("raiderIoGet") {
                                retryEitherWithFixedDelay(retryConfig, "raiderIoGet") {
                                    raiderIoClient.get(it).map { r -> Pair(it.id, r) }
                                }
                            }
                        }
                    }.awaitAll().split()

                val data = errorsAndData.second.map {
                    DataCache(
                        it.first,
                        json.encodeToString<Data>(
                            it.second.profile.toRaiderIoData(
                                it.first,
                                BigDecimal(it.second.profile.mythicPlusRanks.overall.region.toDouble() / cutoff.totalPopulation * 100)
                                    .setScale(
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
            cutoffErrorOrMaybeErrors.mapLeft { listOf(it) }
                .fold(
                    { it },
                    { it }
                )
        }
}
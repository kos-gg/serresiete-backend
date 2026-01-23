package com.kos.sources.wow

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.ClientError
import com.kos.clients.domain.Data
import com.kos.clients.domain.RaiderIoData
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.common.split
import com.kos.datacache.DataCache
import com.kos.datacache.EntitySynchronizer
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.domain.Entity
import com.kos.entities.domain.WowEntity
import com.kos.views.Game
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
            entities as List<WowEntity>

            val cutoffErrorOrMaybeErrors = either {
                val cutoff = execute("raiderIoCutoff") {
                    raiderIoClient.cutoff()
                }.bind()
                val errorsAndData =
                    entities.map {
                        async {
                            execute("raiderIoGet") {
                                raiderIoClient.get(it).map { r -> Pair(it.id, r) }
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

    private suspend fun <A> execute(
        operation: String,
        block: suspend () -> Either<ClientError, A>
    ): Either<ServiceError, A> =
        block().mapLeft { it.toSyncProcessingError(operation) }
}
package com.kos.datacache

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.domain.Data
import com.kos.clients.domain.HardcoreData
import com.kos.clients.domain.RaiderIoData
import com.kos.clients.domain.RiotData
import com.kos.common.WithLogger
import com.kos.common.error.SerializationError
import com.kos.common.error.ServiceError
import com.kos.common.error.toEventPersistenceError
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.domain.EntityDataResponse
import com.kos.entities.domain.EntityRequest
import com.kos.entities.repository.EntitiesRepository
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.Operation
import com.kos.eventsourcing.events.RequestToBeSynced
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.views.Game
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import java.util.*

data class DataCacheService(
    private val dataCacheRepository: DataCacheRepository,
    private val entitiesRepository: EntitiesRepository,
    private val eventStore: EventStore
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

    suspend fun get(entityId: Long): List<DataCache> = dataCacheRepository.get(entityId)
    suspend fun getData(entitiesIds: List<Long>, oldFirst: Boolean): Either<ServiceError, List<Data>> =
        either {
            val comparator: (List<DataCache>) -> DataCache? = if (oldFirst) {
                { it.minByOrNull { dc -> dc.inserted } }
            } else {
                { it.maxByOrNull { dc -> dc.inserted } }
            }

            entitiesIds.mapNotNull { id ->
                comparator(get(id))?.let { dataCache -> parseData(dataCache).bind() }
            }
        }

    suspend fun clearExpired(game: Game?, keepLastRecord: Boolean): Int =
        dataCacheRepository.deleteExpiredRecord(ttl, game, keepLastRecord)

    suspend fun clearCache(game: Game?): Int =
        dataCacheRepository.clearRecords(game)

    suspend fun getOrSync(request: Pair<EntityRequest, Game>): Either<ServiceError, EntityDataResponse> {

        suspend fun syncOperation(entityId: Long): Either<ServiceError, Operation> {
            val eventData = RequestToBeSynced(request.first, request.second)
            return eventStore.save(Event("/entity/$entityId", UUID.randomUUID().toString(), eventData))
                .mapLeft { it.toEventPersistenceError() }
        }

        return either {
            when (val maybeEntity = entitiesRepository.get(request.first, request.second)) {
                null -> EntityDataResponse(null, syncOperation(-1).bind())

                else -> {
                    when (val maybeCachedRecord = get(maybeEntity.id).maxByOrNull { it.inserted }) {
                        null -> EntityDataResponse(null, syncOperation(maybeEntity.id).bind())

                        else -> {
                            if (maybeCachedRecord.isTooOld()) {
                                EntityDataResponse(
                                    parseData(maybeCachedRecord).bind(),
                                    syncOperation(maybeEntity.id).bind()
                                )
                            } else {
                                EntityDataResponse(parseData(maybeCachedRecord).bind(), null)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseData(dataCache: DataCache): Either<ServiceError, Data> {
        return try {
            Either.Right(json.decodeFromString<Data>(dataCache.data))
        } catch (se: SerializationException) {
            Either.Left(SerializationError(dataCache.data, se.stackTraceToString()))
        }
    }
}
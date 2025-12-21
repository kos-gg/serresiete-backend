package com.kos.entities.cache

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.domain.Data
import com.kos.clients.domain.HardcoreData
import com.kos.clients.domain.RaiderIoData
import com.kos.clients.domain.RiotData
import com.kos.clients.toSyncProcessingError
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.Entity
import com.kos.views.Game
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

abstract class EntityCacheService(
    protected val dataCacheRepository: DataCacheRepository
) : WithLogger("EntityCacheService") {

    protected val json = Json {
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

    abstract val game: Game
    abstract suspend fun cache(entities: List<Entity>): List<ServiceError>

    suspend fun <A> execute(
        operation: String,
        block: suspend () -> Either<ClientError, A>
    ): Either<ServiceError, A> =
        block().mapLeft { it.toSyncProcessingError(operation) }
}
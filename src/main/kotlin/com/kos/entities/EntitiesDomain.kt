package com.kos.entities

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.Data
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.common.HttpError
import com.kos.common.NonHardcoreCharacter
import com.kos.common.collect
import com.kos.common.split
import com.kos.entities.repository.EntitiesRepository
import com.kos.eventsourcing.events.Operation
import com.kos.views.Game
import com.kos.views.ViewExtraArguments
import com.kos.views.WowHardcoreExtraArguments
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = WithAliasSerializer::class)
data class WithAlias<T>(
    val value: T,
    val alias: String?
)

@Serializable
sealed interface Entity {
    val id: Long
    val name: String
}

class WithAliasSerializer<T>(private val valueSerializer: KSerializer<T>) : KSerializer<WithAlias<T>> {
    override val descriptor: SerialDescriptor = valueSerializer.descriptor

    override fun serialize(encoder: Encoder, value: WithAlias<T>) {
        val jsonEncoder = encoder as JsonEncoder
        val jsonObject = JsonObject(
            buildMap {
                putAll(jsonEncoder.json.encodeToJsonElement(valueSerializer, value.value).jsonObject)
                put("alias", JsonPrimitive(value.alias))
            }
        )
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): WithAlias<T> {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        val alias = jsonObject["alias"]?.jsonPrimitive?.contentOrNull
        val entityJson = JsonObject(jsonObject - "alias")

        val value = jsonDecoder.json.decodeFromJsonElement(valueSerializer, entityJson)

        return WithAlias(value, alias)
    }
}

@Polymorphic
@Serializable
sealed interface CreateEntityRequest {
    val name: String
    val alias: String?
    fun same(other: Entity): Boolean
}

sealed interface InsertEntityRequest {
    val name: String
    fun toEntity(id: Long): Entity
    fun same(other: Entity): Boolean
}

typealias EntityWithAlias = WithAlias<Entity>
typealias InsertEntityRequestWithAlias = WithAlias<out InsertEntityRequest>

fun <T> T.withAlias(alias: String?): WithAlias<T> = WithAlias(this, alias)

@Serializable
data class EntityDataResponse(val data: Data?, val operation: Operation?)

data class GuildPayload(
    val name: String,
    val realm: String,
    val region: String,
    val blizzardId: Long
)

data class ResolvedEntities(
    val entities: List<Pair<InsertEntityRequest, String?>>,
    val existing: List<Pair<Entity, String?>>,
    val guild: GuildPayload?
)


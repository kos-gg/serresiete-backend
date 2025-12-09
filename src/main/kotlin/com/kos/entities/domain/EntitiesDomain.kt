package com.kos.entities.domain

import com.kos.clients.domain.Data
import com.kos.eventsourcing.events.Operation
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


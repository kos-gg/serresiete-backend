package com.kos.entities.domain

import com.kos.datacache.domain.Data
import com.kos.eventsourcing.events.Operation
import com.kos.views.Game
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
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
    fun toRequest(): EntityRequest
}

@Polymorphic
@Serializable
sealed interface EntityRequest {
    val name: String
    val alias: String?
    fun same(other: Entity): Boolean
    fun toResponse(): EntityResponse
}

fun EntityRequest.belongsTo(game: Game): Boolean = when (game) {
    Game.WOW, Game.WOW_HC -> this is WowEntityRequest
    Game.LOL -> this is LolEntityRequest
    Game.WOW_FOREVER -> this is WowForeverEntityRequest
}

private val blizzardRegions = setOf("eu", "us", "kr", "tw")

fun isBlizzardRegion(region: String): Boolean = region.lowercase() in blizzardRegions

fun EntityRequest.hasValidRegion(): Boolean = when (this) {
    is WowEntityRequest -> isBlizzardRegion(region)
    is WowForeverEntityRequest -> isBlizzardRegion(region)
    is LolEntityRequest -> true
}

fun EntityRequest.missesSurname(): Boolean = this is WowForeverEntityRequest && surname.isBlank()

fun EntityRequest.hasSurname(): Boolean = this is WowForeverEntityRequest && surname.isNotBlank()

fun EntityRequest.normalized(): EntityRequest = when (this) {
    is WowEntityRequest -> copy(name = name.lowercase())
    is WowForeverEntityRequest -> copy(name = name.lowercase(), surname = surname.lowercase())
    is LolEntityRequest -> this
}

@Serializable(with = EntityResponseSerializer::class)
sealed interface EntityResponse {
    val name: String
}

sealed interface InsertEntityRequest {
    val name: String
    fun toEntity(id: Long): Entity
    fun same(other: Entity): Boolean
    fun toRequest(): EntityRequest
}

typealias EntityWithAlias = WithAlias<Entity>
typealias InsertEntityRequestWithAlias = WithAlias<out InsertEntityRequest>

fun <T> T.withAlias(alias: String?): WithAlias<T> = WithAlias(this, alias)

@Serializable
data class EntityDataResponse(val data: Data?, val operation: Operation?)

@Serializable
data class EntitiesExistRequest(
    val entities: List<EntityRequest>,
    val game: Game
)

fun EntitiesExistRequest.normalized(): EntitiesExistRequest = copy(entities = entities.map { it.normalized() })

@Serializable
data class EntitiesExistResponse(
    val exist: List<EntityResponse>,
    val nonExisting: List<EntityResponse>,
    val unchecked: List<EntityResponse>
)

data class ResolvedEntities(
    val entities: List<Pair<InsertEntityRequest, String?>>,
    val existing: List<Pair<Entity, String?>>,
    val unchecked: List<EntityRequest> = emptyList(),
)

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

object EntityResponseSerializer : KSerializer<EntityResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EntityResponse")

    override fun serialize(encoder: Encoder, value: EntityResponse) {
        when (value) {
            is WowEntityResponse -> encoder.encodeSerializableValue(WowEntityResponse.serializer(), value)
            is LolEntityResponse -> encoder.encodeSerializableValue(LolEntityResponse.serializer(), value)
            is WowForeverEntityResponse -> encoder.encodeSerializableValue(WowForeverEntityResponse.serializer(), value)
        }
    }

    override fun deserialize(decoder: Decoder): EntityResponse {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        return when {
            "surname" in jsonObject -> jsonDecoder.json.decodeFromJsonElement(
                WowForeverEntityResponse.serializer(),
                jsonObject
            )

            "region" in jsonObject -> jsonDecoder.json.decodeFromJsonElement(WowEntityResponse.serializer(), jsonObject)
            "tag" in jsonObject -> jsonDecoder.json.decodeFromJsonElement(LolEntityResponse.serializer(), jsonObject)
            else -> throw SerializationException("Cannot determine EntityResponse subtype from $jsonObject")
        }
    }
}

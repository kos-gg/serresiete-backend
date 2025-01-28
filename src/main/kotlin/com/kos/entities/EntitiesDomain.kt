package com.kos.entities

import com.kos.clients.domain.QueueType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable
sealed interface Entity {
    val id: Long
    val name: String
}

@Serializable(with = EntityWithAliasSerializer::class)
data class EntityWithAlias (
    val entity: Entity,
    val alias: String?
)

object EntityWithAliasSerializer : KSerializer<EntityWithAlias> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EntityWithAlias", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: EntityWithAlias) {
        val jsonEncoder = encoder as JsonEncoder
        val jsonObject = JsonObject(
            buildMap {
                // Add entity fields
                putAll(jsonEncoder.json.encodeToJsonElement(value.entity).jsonObject)
                // Add alias
                put("alias", JsonPrimitive(value.alias))
            }
        )
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): EntityWithAlias {
        TODO()
    }
}

//TODO MAX: Afegir camp alias als objectes que faci falta (segur que a CreateEntityRequest)

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
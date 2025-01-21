package com.kos.entities

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

@Serializable
sealed interface Entity {
    val id: Long
    val name: String
}

//TODO MAX: Afegir camp alias als objectes que faci falta (segur que a CreateEntityRequest)

@Polymorphic
@Serializable
sealed interface CreateEntityRequest {
    val name: String
    fun same(other: Entity): Boolean
}

sealed interface InsertEntityRequest {
    val name: String
    fun toEntity(id: Long): Entity
    fun same(other: Entity): Boolean
}
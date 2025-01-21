package com.kos.entities

import com.kos.clients.domain.Data
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

@Serializable
sealed interface Entity {
    val id: Long
    val name: String
}

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

data class EntityDataResponse(val data: Data?, val isBeingSynced: Boolean)
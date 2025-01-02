package com.kos.characters

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

@Serializable
sealed interface Character {
    val id: Long
    val name: String
}

@Polymorphic
@Serializable
sealed interface CharacterCreateRequest {
    val name: String
    fun same(other: Character): Boolean
}

sealed interface CharacterInsertRequest {
    val name: String
    fun toCharacter(id: Long): Character
    fun same(other: Character): Boolean
}
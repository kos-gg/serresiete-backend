package com.kos.characters.repository

import arrow.core.Either
import com.kos.characters.*
import com.kos.common.InsertError
import com.kos.common.WithState
import com.kos.views.Game

data class CharactersState(
    val wowCharacters: List<WowCharacter>,
    val wowHardcoreCharacters: List<WowCharacter>,
    val lolCharacters: List<LolCharacter>
)

interface CharactersRepository : WithState<CharactersState, CharactersRepository> {

    //TODO: insert should be on conflict do nothing so we can avoid the select all + diff on service
    suspend fun insert(characters: List<CharacterInsertRequest>, game: Game): Either<InsertError, List<Character>>
    suspend fun update(id: Long, character: CharacterInsertRequest, game: Game): Either<InsertError, Int>
    suspend fun get(id: Long, game: Game): Character?
    suspend fun get(request: CharacterCreateRequest, game: Game): Character?
    suspend fun get(game: Game): List<Character>
    suspend fun getCharactersToSync(game: Game, olderThanMinutes: Long): List<Character>
    suspend fun get(character: CharacterInsertRequest, game: Game): Character?
    suspend fun getViewsFromCharacter(id: Long, game: Game?): List<String>
    suspend fun delete(id: Long, game: Game): Unit
}
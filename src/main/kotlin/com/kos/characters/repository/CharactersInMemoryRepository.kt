package com.kos.characters.repository

import arrow.core.Either
import com.kos.characters.*
import com.kos.common.InMemoryRepository
import com.kos.common.InsertError
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsInMemoryRepository
import java.time.OffsetDateTime

class CharactersInMemoryRepository(
    private val dataCacheRepository: DataCacheInMemoryRepository = DataCacheInMemoryRepository(),
    private val viewsRepository: ViewsInMemoryRepository = ViewsInMemoryRepository()
) :
    CharactersRepository,
    InMemoryRepository {
    private val wowCharacters: MutableList<WowCharacter> = mutableListOf()
    private val wowHardcoreCharacters: MutableList<WowCharacter> = mutableListOf()
    private val lolCharacters: MutableList<LolCharacter> = mutableListOf()

    private fun nextId(): Long {
        val allIds = wowCharacters.map { it.id } + lolCharacters.map { it.id } + wowHardcoreCharacters.map { it.id }
        return if (allIds.isEmpty()) 1
        else allIds.maxBy { it } + 1
    }

    override suspend fun insert(
        characters: List<CharacterInsertRequest>,
        game: Game
    ): Either<InsertError, List<Character>> {
        val wowInitialCharacters = this.wowCharacters.toList()
        val wowHardcoreInitialCharacters = this.wowHardcoreCharacters.toList()
        val lolInitialCharacters = this.lolCharacters.toList()
        when (game) {
            Game.WOW -> {
                val inserted = characters.fold(listOf<Character>()) { acc, it ->
                    when (it) {
                        is WowCharacterRequest -> {
                            if (this.wowCharacters.any { character -> it.same(character) }) {
                                this.wowCharacters.clear()
                                this.wowCharacters.addAll(wowInitialCharacters)
                                return Either.Left(InsertError("Error inserting character $it"))
                            }
                            val character = it.toCharacter(nextId())
                            this.wowCharacters.add(character)
                            acc + character
                        }

                        is LolCharacterEnrichedRequest, is WowCharacterEnrichedRequest -> {
                            this.wowCharacters.clear()
                            this.wowCharacters.addAll(wowInitialCharacters)
                            return Either.Left(InsertError("Error inserting character $it"))
                        }
                    }
                }
                return Either.Right(inserted)
            }

            Game.LOL -> {
                val inserted = characters.fold(listOf<Character>()) { acc, it ->
                    when (it) {
                        is WowCharacterRequest, is WowCharacterEnrichedRequest -> {
                            this.lolCharacters.clear()
                            this.lolCharacters.addAll(lolInitialCharacters)
                            return Either.Left(InsertError("Error inserting chracter $it"))
                        }

                        is LolCharacterEnrichedRequest -> {
                            if (this.lolCharacters.any { character -> it.same(character) }) {
                                this.lolCharacters.clear()
                                this.lolCharacters.addAll(lolInitialCharacters)
                                return Either.Left(InsertError("Error inserting chracter $it"))
                            }
                            val character = it.toCharacter(nextId())
                            this.lolCharacters.add(character)
                            acc + character
                        }
                    }
                }
                return Either.Right(inserted)
            }

            Game.WOW_HC -> {
                val inserted = characters.fold(listOf<Character>()) { acc, it ->
                    when (it) {
                        is WowCharacterEnrichedRequest -> {
                            if (this.wowHardcoreCharacters.any { character -> it.same(character) }) {
                                this.wowHardcoreCharacters.clear()
                                this.wowHardcoreCharacters.addAll(wowHardcoreInitialCharacters)
                                return Either.Left(InsertError("Error inserting character $it"))
                            }
                            val character = it.toCharacter(nextId())
                            this.wowHardcoreCharacters.add(character)
                            acc + character
                        }

                        is LolCharacterEnrichedRequest, is WowCharacterRequest -> {
                            this.wowHardcoreCharacters.clear()
                            this.wowHardcoreCharacters.addAll(wowInitialCharacters)
                            return Either.Left(InsertError("Error inserting character $it"))
                        }
                    }
                }
                return Either.Right(inserted)
            }
        }
    }

    override suspend fun update(
        id: Long,
        character: CharacterInsertRequest,
        game: Game
    ): Either<InsertError, Int> {
        return when (game) {
            Game.LOL -> when (character) {
                is LolCharacterEnrichedRequest -> {
                    val index = lolCharacters.indexOfFirst { it.id == id }
                    lolCharacters.removeAt(index)
                    val c = LolCharacter(
                        id,
                        character.name,
                        character.tag,
                        character.puuid,
                        character.summonerIconId,
                        character.summonerId,
                        character.summonerLevel
                    )
                    lolCharacters.add(index, c)
                    Either.Right(1)
                }

                else -> Either.Left(InsertError("error updating $id $character for $game"))
            }

            Game.WOW -> when (character) {
                is WowCharacterRequest -> {
                    val index = wowCharacters.indexOfFirst { it.id == id }
                    wowCharacters.removeAt(index)
                    val c = WowCharacter(
                        id,
                        character.name,
                        character.region,
                        character.realm,
                        null
                    )
                    wowCharacters.add(index, c)
                    Either.Right(1)
                }

                else -> Either.Left(InsertError("error updating $id $character for $game"))
            }

            Game.WOW_HC -> when (character) {
                is WowCharacterRequest -> {
                    val index = wowHardcoreCharacters.indexOfFirst { it.id == id }
                    val actualInsertedCharacter = wowHardcoreCharacters[index]
                    wowHardcoreCharacters.removeAt(index)
                    val c = WowCharacter(
                        id,
                        character.name,
                        character.region,
                        character.realm,
                        actualInsertedCharacter.blizzardId
                    )
                    wowHardcoreCharacters.add(index, c)
                    Either.Right(1)
                }

                else -> Either.Left(InsertError("error updating $id $character for $game"))
            }
        }
    }

    override suspend fun get(request: CharacterCreateRequest, game: Game): Character? =
        when (game) {
            Game.WOW -> wowCharacters.find {
                request as WowCharacterRequest
                it.name == request.name &&
                        it.realm == request.realm &&
                        it.region == request.region
            }

            Game.LOL -> lolCharacters.find {
                request as LolCharacterRequest
                it.name == request.name &&
                        it.tag == request.tag
            }

            Game.WOW_HC -> wowHardcoreCharacters.find {
                request as WowCharacterRequest
                it.name == request.name &&
                        it.realm == request.realm &&
                        it.region == request.region
            }
        }

    override suspend fun get(id: Long, game: Game): Character? =
        when (game) {
            Game.WOW -> wowCharacters.find { it.id == id }
            Game.LOL -> lolCharacters.find { it.id == id }
            Game.WOW_HC -> wowHardcoreCharacters.find { it.id == id }
        }

    override suspend fun get(game: Game): List<Character> =
        when (game) {
            Game.WOW -> wowCharacters
            Game.LOL -> lolCharacters
            Game.WOW_HC -> wowHardcoreCharacters
        }

    override suspend fun get(character: CharacterInsertRequest, game: Game): Character? {
        return when (game) {
            Game.WOW -> wowCharacters.find { character.same(it) }
            Game.LOL -> lolCharacters.find { character.same(it) }
            Game.WOW_HC -> wowHardcoreCharacters.find { character.same(it) }
        }
    }

    override suspend fun getCharactersToSync(game: Game, olderThanMinutes: Long): List<Character> {
        val now = OffsetDateTime.now()

        return when (game) {
            Game.WOW -> wowCharacters
            Game.WOW_HC -> wowHardcoreCharacters
            Game.LOL -> {
                lolCharacters.filter { character ->
                    val newestCachedRecord = dataCacheRepository.get(character.id).maxByOrNull { it.inserted }
                    newestCachedRecord == null || newestCachedRecord.inserted.isBefore(now.minusMinutes(olderThanMinutes))
                }
            }
        }
    }

    override suspend fun getViewsFromCharacter(id: Long, game: Game?): List<String> {
        return viewsRepository.getViews(game, false, null, null)
            .second
            .filter { id in it.characterIds }
            .map { it.id }
    }

    override suspend fun delete(id: Long, game: Game) {
        when (game) {
            Game.WOW -> {
                val index = wowCharacters.indexOfFirst { it.id == id }
                wowCharacters.removeAt(index)
            }

            Game.LOL -> {
                val index = lolCharacters.indexOfFirst { it.id == id }
                lolCharacters.removeAt(index)
            }

            Game.WOW_HC -> {
                val index = wowHardcoreCharacters.indexOfFirst { it.id == id }
                wowHardcoreCharacters.removeAt(index)
            }
        }
    }

    override suspend fun state(): CharactersState {
        return CharactersState(wowCharacters, wowHardcoreCharacters, lolCharacters)
    }

    override suspend fun withState(initialState: CharactersState): CharactersInMemoryRepository {
        wowCharacters.addAll(initialState.wowCharacters)
        wowHardcoreCharacters.addAll(initialState.wowHardcoreCharacters)
        lolCharacters.addAll(initialState.lolCharacters)
        return this
    }

    override fun clear() {
        wowCharacters.clear()
        wowHardcoreCharacters.clear()
        lolCharacters.clear()
        dataCacheRepository.clear()
    }

}
package com.kos.characters.repository

import arrow.core.Either
import com.kos.characters.*
import com.kos.common.InsertError
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsDatabaseRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException
import java.time.OffsetDateTime

class CharactersDatabaseRepository(private val db: Database) : CharactersRepository {

    override suspend fun withState(initialState: CharactersState): CharactersDatabaseRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowCharacters.batchInsert(initialState.wowCharacters) {
                this[WowCharacters.id] = it.id
                this[WowCharacters.name] = it.name
                this[WowCharacters.region] = it.region
                this[WowCharacters.realm] = it.realm
            }
            WowHardcoreCharacters.batchInsert(initialState.wowHardcoreCharacters) {
                this[WowHardcoreCharacters.id] = it.id
                this[WowHardcoreCharacters.name] = it.name
                this[WowHardcoreCharacters.region] = it.region
                this[WowHardcoreCharacters.realm] = it.realm
                this[WowHardcoreCharacters.blizzard_id] = it
            }
            LolCharacters.batchInsert(initialState.lolCharacters) {
                this[LolCharacters.id] = it.id
                this[LolCharacters.name] = it.name
                this[LolCharacters.tag] = it.tag
                this[LolCharacters.puuid] = it.puuid
                this[LolCharacters.summonerIcon] = it.summonerIcon
                this[LolCharacters.summonerId] = it.summonerId
                this[LolCharacters.summonerLevel] = it.summonerLevel
            }
        }
        //This needs to be done to consume serial ids. Could be done in a different way but I don't dislike it.
        initialState.lolCharacters.forEach { _ -> selectNextId() }
        initialState.wowCharacters.forEach { _ -> selectNextId() }
        initialState.wowHardcoreCharacters.forEach { _ -> selectNextId() }
        return this
    }

    object WowCharacters : Table("wow_characters") {
        val id = long("id")
        val name = text("name")
        val realm = text("realm")
        val region = text("region")

        override val primaryKey = PrimaryKey(id)
    }

    private fun resultRowToWowCharacter(row: ResultRow) = WowCharacter(
        row[WowCharacters.id],
        row[WowCharacters.name],
        row[WowCharacters.region],
        row[WowCharacters.realm],
        row[WowCharacters.],
    )

    object WowHardcoreCharacters : Table("wow_hardcore_characters") {
        val id = long("id")
        val name = text("name")
        val realm = text("realm")
        val region = text("region")
        val blizzardId = text("blizzard_id")

        override val primaryKey = PrimaryKey(id)
    }

    private fun resultRowToWowHardcoreCharacter(row: ResultRow) = WowCharacter(
        row[WowHardcoreCharacters.id],
        row[WowHardcoreCharacters.name],
        row[WowHardcoreCharacters.region],
        row[WowHardcoreCharacters.realm],
        row[WowHardcoreCharacters.blizzardId]
    )


    object LolCharacters : Table("lol_characters") {
        val id = long("id")
        val name = text("name")
        val tag = text("tag")
        val puuid = text("puuid")
        val summonerIcon = integer("summoner_icon")
        val summonerId = text("summoner_id")
        val summonerLevel = integer("summoner_level")

        override val primaryKey = PrimaryKey(id)
    }

    private fun resultRowToLolCharacter(row: ResultRow) = LolCharacter(
        row[LolCharacters.id],
        row[LolCharacters.name],
        row[LolCharacters.tag],
        row[LolCharacters.puuid],
        row[LolCharacters.summonerIcon],
        row[LolCharacters.summonerId],
        row[LolCharacters.summonerLevel]
    )

    override suspend fun insert(
        characters: List<CharacterInsertRequest>,
        game: Game
    ): Either<InsertError, List<Character>> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            val charsToInsert: List<Character> = characters.map {
                when (it) {
                    is WowCharacterRequest -> WowCharacter(selectNextId(), it.name, it.region, it.realm)
                    is LolCharacterEnrichedRequest -> LolCharacter(
                        selectNextId(),
                        it.name,
                        it.tag,
                        it.puuid,
                        it.summonerIconId,
                        it.summonerId,
                        it.summonerLevel
                    )
                }
            }
            transaction {
                try {
                    val insertedCharacters = when (game) {
                        Game.WOW -> WowCharacters.batchInsert(charsToInsert) {
                            when (it) {
                                is WowCharacter -> {
                                    this[WowCharacters.id] = it.id
                                    this[WowCharacters.name] = it.name
                                    this[WowCharacters.region] = it.region
                                    this[WowCharacters.realm] = it.realm
                                }

                                else -> throw IllegalArgumentException()
                            }
                        }.map { resultRowToWowCharacter(it) }

                        Game.LOL -> LolCharacters.batchInsert(charsToInsert) {
                            when (it) {
                                is WowCharacter -> throw IllegalArgumentException()
                                is LolCharacter -> {
                                    this[LolCharacters.id] = it.id
                                    this[LolCharacters.name] = it.name
                                    this[LolCharacters.tag] = it.tag
                                    this[LolCharacters.puuid] = it.puuid
                                    this[LolCharacters.summonerIcon] = it.summonerIcon
                                    this[LolCharacters.summonerId] = it.summonerId
                                    this[LolCharacters.summonerLevel] = it.summonerLevel
                                }
                            }
                        }.map { resultRowToLolCharacter(it) }

                        Game.WOW_HC -> WowHardcoreCharacters.batchInsert(charsToInsert) {
                            when (it) {
                                is WowCharacter -> {
                                    this[WowHardcoreCharacters.id] = it.id
                                    this[WowHardcoreCharacters.name] = it.name
                                    this[WowHardcoreCharacters.region] = it.region
                                    this[WowHardcoreCharacters.realm] = it.realm
                                }

                                else -> throw IllegalArgumentException()
                            }
                        }.map { resultRowToWowHardcoreCharacter(it) }
                    }
                    Either.Right(insertedCharacters)
                } catch (e: SQLException) {
                    rollback() //TODO: I don't understand why rollback is not provided by dbQuery.
                    Either.Left(InsertError(e.message ?: e.stackTraceToString()))
                } catch (e: IllegalArgumentException) {
                    rollback() //TODO: I don't understand why rollback is not provided by dbQuery.
                    Either.Left(InsertError(e.message ?: e.stackTraceToString()))
                }
            }
        }
    }

    override suspend fun update(
        id: Long,
        character: CharacterInsertRequest,
        game: Game
    ): Either<InsertError, Int> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            when (game) {
                Game.LOL -> {
                    when (character) {
                        is LolCharacterEnrichedRequest -> {
                            Either.Right(LolCharacters.update({ LolCharacters.id eq id }) {
                                it[name] = character.name
                                it[tag] = character.tag
                                it[puuid] = character.puuid
                                it[summonerIcon] = character.summonerIconId
                                it[summonerId] = character.summonerId
                                it[summonerLevel] = character.summonerLevel
                            })
                        }

                        else -> Either.Left(InsertError("problem updating $id: $character for $game"))
                    }
                }

                Game.WOW -> when (character) {
                    is WowCharacterRequest -> {
                        Either.Right(WowCharacters.update({ WowCharacters.id eq id }) {
                            it[name] = character.name
                            it[region] = character.region
                            it[realm] = character.realm
                        })
                    }

                    else -> Either.Left(InsertError("problem updating $id: $character for $game"))
                }

                Game.WOW_HC -> when (character) {
                    is WowCharacterRequest -> {
                        Either.Right(WowHardcoreCharacters.update({ WowHardcoreCharacters.id eq id }) {
                            it[name] = character.name
                            it[region] = character.region
                            it[realm] = character.realm
                        })
                    }

                    else -> Either.Left(InsertError("problem updating $id: $character for $game"))
                }
            }
        }
    }

    override suspend fun get(id: Long, game: Game): Character? {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            when (game) {
                Game.WOW -> WowCharacters.selectAll().where { WowCharacters.id.eq(id) }.singleOrNull()?.let {
                    resultRowToWowCharacter(it)
                }

                Game.LOL -> LolCharacters.selectAll().where { LolCharacters.id.eq(id) }.singleOrNull()?.let {
                    resultRowToLolCharacter(it)
                }

                Game.WOW_HC -> WowHardcoreCharacters.selectAll().where { WowHardcoreCharacters.id.eq(id) }.singleOrNull()?.let {
                    resultRowToWowHardcoreCharacter(it)
                }
            }
        }
    }

    override suspend fun get(request: CharacterCreateRequest, game: Game): Character? {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            when (game) {
                Game.WOW -> {
                    request as WowCharacterRequest
                    WowCharacters.selectAll().where {
                        WowCharacters.name.eq(request.name)
                            .and(WowCharacters.realm.eq(request.realm))
                            .and(WowCharacters.region.eq(request.region))
                    }.map { resultRowToWowCharacter(it) }
                }

                Game.LOL -> {
                    request as LolCharacterRequest
                    LolCharacters.selectAll().where {
                        LolCharacters.tag.eq(request.tag)
                            .and(LolCharacters.name.eq(request.name))
                    }.map { resultRowToLolCharacter(it) }
                }

                Game.WOW_HC -> {
                    request as WowCharacterRequest
                    WowHardcoreCharacters.selectAll().where {
                        WowHardcoreCharacters.name.eq(request.name)
                            .and(WowHardcoreCharacters.realm.eq(request.realm))
                            .and(WowHardcoreCharacters.region.eq(request.region))
                    }.map { resultRowToWowHardcoreCharacter(it) }
                }
            }.singleOrNull()
        }
    }

    override suspend fun get(character: CharacterInsertRequest, game: Game): Character? {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            when (game) {
                Game.WOW -> {
                    character as WowCharacterRequest
                    WowCharacters.selectAll().where {
                        WowCharacters.name.eq(character.name)
                            .and(WowCharacters.realm.eq(character.realm))
                            .and(WowCharacters.region.eq(character.region))
                    }.map { resultRowToWowCharacter(it) }
                }

                Game.LOL -> {
                    character as LolCharacterEnrichedRequest
                    LolCharacters.selectAll().where {
                        LolCharacters.puuid.eq(character.puuid)
                            .and(LolCharacters.summonerId.eq(character.summonerId))
                    }.map { resultRowToLolCharacter(it) }
                }

                Game.WOW_HC -> {
                    character as WowCharacterRequest
                    WowHardcoreCharacters.selectAll().where {
                        WowHardcoreCharacters.name.eq(character.name)
                            .and(WowHardcoreCharacters.realm.eq(character.realm))
                            .and(WowHardcoreCharacters.region.eq(character.region))
                    }.map { resultRowToWowHardcoreCharacter(it) }
                }
            }
        }.singleOrNull()
    }

    override suspend fun get(game: Game): List<Character> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            when (game) {
                Game.WOW -> WowCharacters.selectAll().map { resultRowToWowCharacter(it) }
                Game.LOL -> LolCharacters.selectAll().map { resultRowToLolCharacter(it) }
                Game.WOW_HC -> WowHardcoreCharacters.selectAll().map { resultRowToWowHardcoreCharacter(it) }
            }
        }

    override suspend fun getCharactersToSync(game: Game, olderThanMinutes: Long): List<Character> {

        return newSuspendedTransaction(Dispatchers.IO, db) {
            when (game) {
                Game.WOW -> WowCharacters.selectAll().map { resultRowToWowCharacter(it) }
                Game.LOL -> {
                    val subQuery = DataCacheDatabaseRepository.DataCaches
                        .select(
                            DataCacheDatabaseRepository.DataCaches.characterId,
                            DataCacheDatabaseRepository.DataCaches.inserted.max().alias("inserted")
                        )
                        .groupBy(DataCacheDatabaseRepository.DataCaches.characterId)

                    val subQueryAliased = subQuery.alias("dc")

                    val thirtyMinutesAgo = OffsetDateTime.now().minusMinutes(olderThanMinutes).toString()
                    LolCharacters
                        .leftJoin(
                            subQueryAliased,
                            { id },
                            { subQueryAliased[DataCacheDatabaseRepository.DataCaches.characterId] })
                        .selectAll().where {
                            (subQueryAliased[DataCacheDatabaseRepository.DataCaches.inserted].isNull()) or
                                    (subQueryAliased[DataCacheDatabaseRepository.DataCaches.inserted] lessEq thirtyMinutesAgo)
                        }
                        .map { resultRowToLolCharacter(it) }
                }

                Game.WOW_HC -> WowHardcoreCharacters.selectAll().map { resultRowToWowHardcoreCharacter(it) }
            }
        }
    }

    override suspend fun getViewsFromCharacter(id: Long, game: Game?): List<String> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            ViewsDatabaseRepository.CharactersView.selectAll().where { ViewsDatabaseRepository.CharactersView.characterId.eq(id) }
                .map {
                    it[ViewsDatabaseRepository.CharactersView.viewId]
                }
        }
    }

    override suspend fun delete(id: Long, game: Game) {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            when (game) {
                Game.WOW -> WowCharacters.deleteWhere { WowCharacters.id.eq(id) }
                Game.LOL -> LolCharacters.deleteWhere { LolCharacters.id.eq(id) }
                Game.WOW_HC -> WowHardcoreCharacters.deleteWhere { WowHardcoreCharacters.id.eq(id) }
            }
        }
    }


    override suspend fun state(): CharactersState {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            CharactersState(
                WowCharacters.selectAll().map { resultRowToWowCharacter(it) },
                WowHardcoreCharacters.selectAll().map { resultRowToWowHardcoreCharacter(it) },
                LolCharacters.selectAll().map { resultRowToLolCharacter(it) }
            )
        }
    }

    private suspend fun selectNextId(): Long =
        newSuspendedTransaction(Dispatchers.IO, db) {
            TransactionManager.current().exec("""select nextval('characters_ids') as id""") { rs ->
                if (rs.next()) rs.getLong("id")
                else -1
            }
        } ?: -1
}
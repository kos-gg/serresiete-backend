package com.kos.characters

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kos.characters.repository.CharactersRepository
import com.kos.common.InsertError
import com.kos.common.WithLogger
import com.kos.common.collect
import com.kos.common.split
import com.kos.common.*
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.GetWowRealmResponse
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.views.Game
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CharactersService(
    private val charactersRepository: CharactersRepository,
    private val raiderioClient: RaiderIoClient,
    private val riotClient: RiotClient,
    private val blizzardClient: BlizzardClient
) : WithLogger("CharactersService") {
    suspend fun createAndReturnIds(
        requestedCharacters: List<CharacterCreateRequest>,
        game: Game
    ): Either<InsertError, List<Long>> {
        suspend fun getCurrentAndNewCharacters(
            requestedCharacters: List<CharacterCreateRequest>,
            game: Game,
            charactersRepository: CharactersRepository
        ): Pair<List<Character>, List<CharacterCreateRequest>> = coroutineScope {

            val characters = requestedCharacters.asFlow()
                .map { requestedCharacter ->
                    async {
                        when (val maybeFound = charactersRepository.get(requestedCharacter, game)) {
                            null -> Either.Right(requestedCharacter)
                            else -> Either.Left(maybeFound)
                        }
                    }
                }
                .buffer(3)
                .toList()
                .awaitAll()

            characters.split()
        }

        val existentAndNew = getCurrentAndNewCharacters(requestedCharacters, game, charactersRepository)

        existentAndNew.second.forEach { logger.info("Character new found: $it") }

        val newThatExist = when (game) {
            Game.WOW -> {
                coroutineScope {
                    existentAndNew.second.map { initialRequest ->
                        async {
                            initialRequest as WowCharacterRequest
                            initialRequest to raiderioClient.exists(initialRequest)
                        }
                    }
                        .awaitAll()
                }.collect({ it.second }) { it.first }
            }

            Game.WOW_HC -> {
                coroutineScope {
                    val errorsAndValidated = existentAndNew.second.map { initialRequest ->
                        async {
                            either {
                                initialRequest as WowCharacterRequest
                                val characterResponse =
                                    blizzardClient.getCharacterProfile(
                                        initialRequest.region,
                                        initialRequest.realm,
                                        initialRequest.name
                                    ).bind()
                                val realm: GetWowRealmResponse =
                                    blizzardClient.getRealm(initialRequest.region, characterResponse.realm.id).bind()
                                //TODO: Anniversary can be also non hardcore. Try to find another way to decide if its hardcore or not
                                ensure(realm.category == "Hardcore" || realm.category == "Anniversary") { NonHardcoreCharacter(initialRequest) }
                                initialRequest
                            }
                        }
                    }.awaitAll().split()
                    errorsAndValidated.first.forEach { logger.error(it.error()) }
                    errorsAndValidated.second
                }
            }

            Game.LOL -> coroutineScope {
                existentAndNew.second.asFlow()
                    .buffer(40)
                    .mapNotNull { initialRequest ->
                        either {
                            initialRequest as LolCharacterRequest
                            val puuid = riotClient.getPUUIDByRiotId(initialRequest.name, initialRequest.tag)
                                .onLeft { error -> logger.error(error.error()) }
                                .bind()
                            val summonerResponse = riotClient.getSummonerByPuuid(puuid.puuid)
                                .onLeft { error -> logger.error(error.error()) }
                                .bind()
                            LolCharacterEnrichedRequest(
                                initialRequest.name,
                                initialRequest.tag,
                                summonerResponse.puuid,
                                summonerResponse.profileIconId,
                                summonerResponse.id,
                                summonerResponse.summonerLevel
                            )
                        }.getOrNull()
                    }
                    .buffer(3)
                    .filterNot { characterToInsert -> charactersRepository.get(characterToInsert, game).isDefined() }
                    .toList()
            }
        }

        return charactersRepository.insert(newThatExist, game)
            .map { list -> list.map { it.id } + existentAndNew.first.map { it.id } }
    }

    suspend fun updateLolCharacters(characters: List<LolCharacter>): List<ControllerError> =
        coroutineScope {
            val errorsChannel = Channel<ControllerError>()
            val dataChannel = Channel<Pair<LolCharacterEnrichedRequest, Long>>()
            val errorsList = mutableListOf<ControllerError>()

            val errorsCollector = launch {
                errorsChannel.consumeAsFlow().collect { error ->
                    logger.error(error.toString())
                    errorsList.add(error)
                }
            }

            val dataCollector = launch {
                dataChannel.consumeAsFlow()
                    .buffer(40)
                    .collect { characterWithId ->
                        charactersRepository.update(characterWithId.second, characterWithId.first, Game.LOL)
                        logger.info("updated character ${characterWithId.second}")
                    }
            }

            characters.asFlow()
                .buffer(40)
                .collect { lolCharacter ->
                    val result = retrieveUpdatedLolCharacter(lolCharacter)
                    result.fold(
                        ifLeft = { error -> errorsChannel.send(error) },
                        ifRight = { dataChannel.send(Pair(it, lolCharacter.id)) }
                    )
                }
            dataChannel.close()
            errorsChannel.close()

            errorsCollector.join()
            dataCollector.join()

            logger.info("Finished Updating Lol characters")
            errorsList
        }

    private suspend fun retrieveUpdatedLolCharacter(lolCharacter: LolCharacter): Either<HttpError, LolCharacterEnrichedRequest> =
        either {
            val summoner = riotClient.getSummonerByPuuid(lolCharacter.puuid).bind()
            val account = riotClient.getAccountByPUUID(lolCharacter.puuid).bind()
            LolCharacterEnrichedRequest(
                name = account.gameName,
                tag = account.tagLine,
                puuid = lolCharacter.puuid,
                summonerIconId = summoner.profileIconId,
                summonerId = summoner.id,
                summonerLevel = summoner.summonerLevel
            )
        }


    suspend fun get(id: Long, game: Game): Character? = charactersRepository.get(id, game)
    suspend fun get(game: Game): List<Character> = charactersRepository.get(game)
    suspend fun getCharactersToSync(game: Game, olderThanMinutes: Long) =
        charactersRepository.getCharactersToSync(game, olderThanMinutes)
}
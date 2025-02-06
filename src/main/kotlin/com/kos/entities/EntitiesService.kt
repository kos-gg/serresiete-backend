package com.kos.entities

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.GetWowRealmResponse
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.common.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class EntitiesService(
    private val entitiesRepository: EntitiesRepository,
    private val raiderioClient: RaiderIoClient,
    private val riotClient: RiotClient,
    private val blizzardClient: BlizzardClient
) : WithLogger("EntitiesService") {
    suspend fun createAndReturnIds(
        requestedEntities: List<CreateEntityRequest>,
        game: Game
    ): Either<InsertError, List<Entity>> {
        suspend fun getCurrentAndNewEntities(
            requestedEntities: List<CreateEntityRequest>,
            game: Game,
            entitiesRepository: EntitiesRepository
        ): Pair<List<Entity>, List<CreateEntityRequest>> = coroutineScope {

            val entities = requestedEntities.asFlow()
                .map { requestedEntity ->
                    async {
                        when (val maybeFound = entitiesRepository.get(requestedEntity, game)) {
                            null -> Either.Right(requestedEntity)
                            else -> Either.Left(maybeFound)
                        }
                    }
                }
                .buffer(3)
                .toList()
                .awaitAll()

            entities.split()
        }

        val existentAndNew = getCurrentAndNewEntities(requestedEntities, game, entitiesRepository)

        existentAndNew.second.forEach { logger.info("Entity new found: $it") }

        val newThatExist = when (game) {
            Game.WOW -> {
                coroutineScope {
                    existentAndNew.second.map { initialRequest ->
                        async {
                            initialRequest as WowEntityRequest
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
                                initialRequest as WowEntityRequest
                                val characterResponse =
                                    blizzardClient.getCharacterProfile(
                                        initialRequest.region,
                                        initialRequest.realm,
                                        initialRequest.name
                                    ).bind()
                                val realm: GetWowRealmResponse =
                                    blizzardClient.getRealm(initialRequest.region, characterResponse.realm.id).bind()
                                //TODO: Anniversary can be also non hardcore. Try to find another way to decide if its hardcore or not
                                ensure(realm.category == "Hardcore" || realm.category == "Anniversary") {
                                    NonHardcoreCharacter(
                                        initialRequest
                                    )
                                }
                                WowEnrichedEntityRequest(
                                    initialRequest.name,
                                    initialRequest.region,
                                    initialRequest.realm,
                                    characterResponse.id
                                )
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
                            initialRequest as LolEntityRequest
                            val puuid = riotClient.getPUUIDByRiotId(initialRequest.name, initialRequest.tag)
                                .onLeft { error -> logger.error(error.error()) }
                                .bind()
                            val summonerResponse = riotClient.getSummonerByPuuid(puuid.puuid)
                                .onLeft { error -> logger.error(error.error()) }
                                .bind()
                            LolEnrichedEntityRequest(
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
                    .filterNot { entityToInsert -> entitiesRepository.get(entityToInsert, game).isDefined() }
                    .toList()
            }
        }

        return entitiesRepository.insert(newThatExist, game)
            .map { list -> list + existentAndNew.first }
    }

    suspend fun updateLolEntities(entities: List<LolEntity>): List<ControllerError> =
        coroutineScope {
            val errorsChannel = Channel<ControllerError>()
            val dataChannel = Channel<Pair<LolEnrichedEntityRequest, Long>>()
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
                    .collect { entityWithId ->
                        entitiesRepository.update(entityWithId.second, entityWithId.first, Game.LOL)
                        logger.info("updated eEntity ${entityWithId.second}")
                    }
            }

            entities.asFlow()
                .buffer(40)
                .collect { lolEntity ->
                    val result = retrieveUpdatedLolEntity(lolEntity)
                    result.fold(
                        ifLeft = { error -> errorsChannel.send(error) },
                        ifRight = { dataChannel.send(Pair(it, lolEntity.id)) }
                    )
                }
            dataChannel.close()
            errorsChannel.close()

            errorsCollector.join()
            dataCollector.join()

            logger.info("Finished Updating Lol entities")
            errorsList
        }

    private suspend fun retrieveUpdatedLolEntity(lolEntity: LolEntity): Either<HttpError, LolEnrichedEntityRequest> =
        either {
            val summoner = riotClient.getSummonerByPuuid(lolEntity.puuid).bind()
            val account = riotClient.getAccountByPUUID(lolEntity.puuid).bind()
            LolEnrichedEntityRequest(
                name = account.gameName,
                tag = account.tagLine,
                puuid = lolEntity.puuid,
                summonerIconId = summoner.profileIconId,
                summonerId = summoner.id,
                summonerLevel = summoner.summonerLevel
            )
        }


    suspend fun get(id: Long, game: Game): Entity? = entitiesRepository.get(id, game)
    suspend fun get(game: Game): List<Entity> = entitiesRepository.get(game)
    suspend fun getEntitiesToSync(game: Game, olderThanMinutes: Long) =
        entitiesRepository.getEntitiesToSync(game, olderThanMinutes)

    suspend fun getViewsFromEntity(id: Long, game: Game?): List<String> =
        entitiesRepository.getViewsFromEntity(id, game)

    suspend fun delete(id: Long): Unit = entitiesRepository.delete(id)
}
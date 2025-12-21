package com.kos.entities.entitiesUpdaters

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.ClientError
import com.kos.clients.riot.RiotClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.entities.LolEnrichedEntityRequest
import com.kos.entities.LolEntity
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

data class LolUpdater(private val riotClient: RiotClient, private val repository: EntitiesRepository) :
    EntityUpdater<LolEntity>, WithLogger("LolUpdater") {
    override suspend fun update(entities: List<LolEntity>): List<ServiceError> =
        coroutineScope {
            val dataChannel = Channel<Pair<LolEnrichedEntityRequest, Long>>()
            val errorsChannel = Channel<ServiceError>()
            val errorsList = mutableListOf<ServiceError>()

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
                        repository.update(entityWithId.second, entityWithId.first, Game.LOL)
                        logger.info("updated eEntity ${entityWithId.second}")
                    }
            }

            entities.asFlow()
                .buffer(40)
                .collect { lolEntity ->
                    retrieveUpdatedLolEntity(lolEntity).fold(
                        ifLeft = { error -> errorsChannel.send(error.toSyncProcessingError("Retrieve Updated Lol Entity")) },
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

    private suspend fun retrieveUpdatedLolEntity(lolEntity: LolEntity): Either<ClientError, LolEnrichedEntityRequest> =
        either {
            val summoner = riotClient.getSummonerByPuuid(lolEntity.puuid).bind()
            val account = riotClient.getAccountByPUUID(lolEntity.puuid).bind()
            LolEnrichedEntityRequest(
                name = account.gameName,
                tag = account.tagLine,
                puuid = lolEntity.puuid,
                summonerIconId = summoner.profileIconId,
                summonerLevel = summoner.summonerLevel
            )
        }
}
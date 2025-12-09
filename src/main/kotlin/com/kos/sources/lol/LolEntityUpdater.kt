package com.kos.sources.lol

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.riot.RiotClient
import com.kos.common.ControllerError
import com.kos.common.HttpError
import com.kos.common.WithLogger
import com.kos.entities.EntityUpdater
import com.kos.entities.domain.LolEnrichedEntityRequest
import com.kos.entities.domain.LolEntity
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

data class LolEntityUpdater(private val riotClient: RiotClient, private val repository: EntitiesRepository): EntityUpdater<LolEntity>, WithLogger("LolUpdater") {
    override suspend fun update(entities: List<LolEntity>): List<ControllerError> =
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
                        repository.update(entityWithId.second, entityWithId.first, Game.LOL)
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
                summonerLevel = summoner.summonerLevel
            )
        }
}
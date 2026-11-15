package com.kos.entities

import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesState

object EntitiesTestHelper {
    val emptyEntitiesState = EntitiesState(listOf(), listOf(), listOf())
    val basicWowRequest = WowEntityRequest("kakarona", "eu", "zuljin")
    val basicWowRequestWithBlizzardId = WowEntityRequest("kakarona", "eu", "zuljin", 12345)
    val basicWowRequest2 = WowEntityRequest("layser", "eu", "zuljin")
    val basicWowRequest2WithBlizzardId = basicWowRequest2.copy(blizzardId = 67890)
    val basicLolEntity = LolEntity(1, "GTP ZeroMVPs", "WOW", "1", 1, 1)
    val basicLolEntity2 = LolEntity(2, "Sanxei", "EUW", "2", 2, 2)
    val basicLolEntityEnrichedRequest = LolEnrichedEntityRequest(
        basicLolEntity.name,
        basicLolEntity.tag,
        basicLolEntity.puuid,
        basicLolEntity.summonerIcon,
        basicLolEntity.summonerLevel
    )
    val basicWowEntity = basicWowRequestWithBlizzardId.toEntity(1)
    val basicWowHardcoreEntity = basicWowEntity.copy(blizzardId = 12345)
    val basicWowEntity2 = basicWowRequest2WithBlizzardId.toEntity(2)
    val basicWowForeverRequest = WowForeverEntityRequest("sanxei", "tarde", "eu")
    val basicWowForeverRequestWithBlizzardId = basicWowForeverRequest.copy(blizzardId = 6880137)
    val basicWowForeverEntity = basicWowForeverRequestWithBlizzardId.toEntity(1)
    val basicLolSummoner = LolSummoner("1", 2, 29)
    val basicRiotAccount = RiotAccount(
        "Marcnute",
        "EUW"
    )
    val basicPuuid = "1"
    val gigaLolCharacterRequestList = List(14) { index ->
        LolEntityRequest("sanxei$index", "euw$index")
    }
    val gigaLolEntityList = List(7) { index ->
        LolEntity(index.toLong(), "sanxei$index", "euw$index", index.toString(), index, 400)
    }
    val lolEntityRequest = LolEntityRequest("sanxei", "EUW")
}
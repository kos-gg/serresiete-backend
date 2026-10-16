package com.kos.datacache

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.datacache.domain.MythicPlusRank
import com.kos.datacache.domain.MythicPlusRanksWithSpecs
import com.kos.datacache.domain.RaiderIoData
import com.kos.datacache.domain.SeasonCutoff
import com.kos.datacache.domain.WowCharacterProfile
import com.kos.entities.domain.WowEntity

object RaiderIoMockHelper {
    fun get(wowCharacter: WowEntity): Either<ClientError, WowCharacterProfile> {
        return Either.Right(
            WowCharacterProfile(
                wowCharacter.name,
                wowCharacter.realm,
                wowCharacter.region,
                "class",
                "spec",
                0.0,
                MythicPlusRank(1, 1, 1),
                MythicPlusRank(1, 1, 1),
                listOf(),
                listOf(),
                listOf()
            )
        )
    }

    fun cutoff(): Either<ClientError, SeasonCutoff> {
        return Either.Right(SeasonCutoff(100))
    }

    val raiderIoData = listOf(
        RaiderIoData(
            id = 1,
            name = "kakarona",
            realm = "sanguino",
            region = "eu",
            score = 0.0,
            `class` = "class",
            spec = "spec",
            quantile = 1.0,
            mythicPlusRanks = MythicPlusRanksWithSpecs(
                overall = MythicPlusRank(world = 1, region = 1, realm = 1),
                `class` = MythicPlusRank(world = 1, region = 1, realm = 1),
                specs = listOf()
            ),
            mythicPlusBestRuns = listOf(),
            mythicPlusRecentRuns = listOf()
        )
    )

    val raiderIoDataString = """
        {
          "type": "com.kos.clients.domain.RaiderIoData",
          "id": 1,
          "name": "kakarona",
          "realm": "sanguino",
          "region": "eu",
          "score": 0.0,
          "class": "class",
          "spec": "spec",
          "quantile": 1.0,
          "mythicPlusRanks": {
            "overall": {
              "world": 1,
              "region": 1,
              "realm": 1
            },
            "class": {
              "world": 1,
              "region": 1,
              "realm": 1
            },
            "specs": [
              
            ]
          },
          "mythicPlusBestRuns": [
            
          ]
        }
    """.trimIndent()

    val raiderioCachedData: RaiderIoData = RaiderIoData(
        id = 1,
        name = "Proassassin",
        realm = "Sanguino",
        region = "eu",
        score = 0.0,
        `class` = "Demon Hunter",
        spec = "Havoc",
        quantile = 0.0,
        mythicPlusRanks = MythicPlusRanksWithSpecs(
            overall = MythicPlusRank(world = 0, region = 0, realm = 0),
            `class` = MythicPlusRank(world = 0, region = 0, realm = 0),
            specs = listOf()
        ),
        mythicPlusBestRuns = listOf(),
        mythicPlusRecentRuns = listOf()
    )


}
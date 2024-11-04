package com.kos.datacache

import arrow.core.Either
import com.kos.characters.WowCharacter
import com.kos.common.HttpError
import com.kos.httpclients.domain.*

object RaiderIoMockHelper {
    fun get(wowCharacter: WowCharacter): Either<HttpError, RaiderIoResponse> {
        return Either.Right(
            RaiderIoResponse(
                RaiderIoProfile(
                    wowCharacter.name,
                    "class",
                    "spec",
                    listOf(MythicPlusSeasonScore("df-3", SeasonScores(0.0, 0.0, 0.0, 0.0, 0.0))),
                    MythicPlusRanks(
                        MythicPlusRank(1, 1, 1),
                        MythicPlusRank(1, 1, 1)
                    ),
                    listOf()
                ),
                listOf()
            )
        )
    }

    fun cutoff(): Either<HttpError, RaiderIoCutoff> {
        return Either.Right(RaiderIoCutoff(100))
    }

    val raiderIoData = listOf(
        RaiderIoData(
            id = 1,
            name = "kakarona",
            score = 0.0,
            `class` = "class",
            spec = "spec",
            quantile = 1.0,
            mythicPlusRanks = MythicPlusRanksWithSpecs(
                overall = MythicPlusRank(world = 1, region = 1, realm = 1),
                `class` = MythicPlusRank(world = 1, region = 1, realm = 1),
                specs = listOf()
            ), mythicPlusBestRuns = listOf()
        )
    )

    val raiderioCachedData: RaiderIoData = RaiderIoData(
        id = 1,
        name = "Proassassin",
        score = 0.0,
        `class` = "Demon Hunter",
        spec = "Havoc",
        quantile = 0.0,
        mythicPlusRanks = MythicPlusRanksWithSpecs(
            overall = MythicPlusRank(world = 0, region = 0, realm = 0),
            `class` = MythicPlusRank(world = 0, region = 0, realm = 0),
            specs = listOf()
        ),
        mythicPlusBestRuns = listOf()
    )


}
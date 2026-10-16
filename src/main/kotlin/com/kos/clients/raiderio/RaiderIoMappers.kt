package com.kos.clients.raiderio

import com.kos.clients.domain.*
import com.kos.datacache.domain.*
import com.kos.sources.wow.staticdata.wowseason.Dungeon
import com.kos.sources.wow.staticdata.wowseason.Season

fun RaiderIoProfile.toWowCharacterProfile(specRanks: List<MythicPlusRankWithSpecName>) = WowCharacterProfile(
    name = name,
    realm = realm,
    region = region,
    `class` = `class`,
    spec = spec,
    score = seasonScores[0].scores.all,
    overallRank = mythicPlusRanks.overall.toMythicPlusRank(),
    classRank = mythicPlusRanks.`class`.toMythicPlusRank(),
    specRanks = specRanks,
    bestRuns = mythicPlusBestRuns.map { it.toMythicPlusRun() },
    recentRuns = mythicPlusRecentRuns.map { it.toMythicPlusRun() }
)

fun MythicPlusRankResponse.toMythicPlusRank() = MythicPlusRank(world, region, realm)

fun MythicPlusRunResponse.toMythicPlusRun() = MythicPlusRun(
    runId = runId,
    dungeon = dungeon,
    shortName = shortName,
    keyLevel = keyLevel,
    upgrades = upgrades,
    dateCompleted = dateCompleted,
    clearTimeMs = clearTimeMs,
    dungeonTimeMs = dungeonTimeMs,
    score = score,
    url = url,
    affixes = affixes.map { Affix(it.affix) },
    spec = spec?.let { ClassSpec(it.id, it.name, it.role) }
)

fun RunDetailsResponse.toRunDetails() = RunDetails(
    roster = roster.map { entry ->
        RunDetailsRosterEntry(
            character = with(entry.character) {
                RunDetailsCharacter(
                    name,
                    RunDetailsCharacterClass(`class`.name),
                    RunDetailsCharacterSpec(spec.name),
                    RunDetailsCharacterRealm(realm.id, realm.name, realm.slug),
                    RunDetailsCharacterRegion(region.name, region.shortName, region.slug)
                )
            },
            role = entry.role,
            ranks = RunDetailsRosterRanks(entry.ranks.score)
        )
    },
    loggedDetails = loggedDetails?.let { details ->
        LoggedDetails(details.deaths.map { RunDetailsDeath(it.approximateDiedAt) })
    }
)

fun SeasonResponse.toSeason() = Season(
    isCurrentSeason = isCurrentSeason,
    name = name,
    slug = slug,
    blizzardSeasonId = blizzardSeasonId,
    dungeons = dungeons.map { Dungeon(it.name, it.shortName, it.dungeonId, it.icon) }
)

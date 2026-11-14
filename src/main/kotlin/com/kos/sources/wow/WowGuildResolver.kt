package com.kos.sources.wow

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.recover
import arrow.fx.coroutines.parMapUnordered
import com.kos.clients.ClientError
import com.kos.clients.NotFound
import com.kos.clients.blizzard.BlizzardClient
import com.kos.common.WithLogger
import com.kos.common.split
import com.kos.entities.GuildMembers
import com.kos.entities.GuildResolver
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonRepository
import com.kos.views.Game
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

@OptIn(FlowPreview::class)
class WowGuildResolver(
    override val entitiesRepository: EntitiesRepository,
    private val blizzardClient: BlizzardClient,
    private val wowSeasonRepository: WowSeasonRepository
) : GuildResolver<WowRealmGuild, WowEntityRequest>, WithLogger("WowGuildResolver") {
    companion object {
        private const val MAX_CHARACTER_LEVEL = 90
        private const val SCORE_CUTOFF = 1000
    }

    override val game = Game.WOW

    override suspend fun fetchRoster(guild: EntityRequest): GuildMembers<WowRealmGuild, WowEntityRequest> {
        val guildReq = guild as WowEntityRequest
        logger.info("Resolving guild ${guildReq.name} (${guildReq.region}/${guildReq.realm})")

        val (guildRoster, members) = resolveRoster(guildReq.region, guildReq.realm, guildReq.name)
        return GuildMembers(payload(guildRoster, guildReq), members)
    }

    suspend fun exists(guild: WowEntityRequest): Either<ClientError, WowRealmGuild> =
        blizzardClient.getRetailGuildRoster(guild.region, guild.realm, guild.name).map { payload(it, guild) }

    override suspend fun admit(newMembers: List<WowEntityRequest>): List<Pair<InsertEntityRequest, String?>> {
        val seasonId = checkNotNull(wowSeasonRepository.getCurrentSeason()?.id) { "There is no current mythic+ season" }

        val (failed, scored) = newMembers.asFlow()
            .parMapUnordered(10) { req -> scoreOf(req, seasonId) }
            .toList()
            .split()

        val (competitive, belowScore) = scored.partition { (_, score) -> score >= SCORE_CUTOFF }

        logger.info(
            "Resolved ${newMembers.size} new guild members: ${competitive.size} kept, " +
                    "${belowScore.size} below minimum mythic+ score, ${failed.size} failed"
        )

        return competitive.map { (req, _) -> req to req.alias }
    }

    private suspend fun scoreOf(
        req: WowEntityRequest,
        seasonId: Int
    ): Either<ClientError, Pair<WowEntityRequest, Double>> =
        either {
            recover({
                req to blizzardClient.getMythicKeystoneProfile(req.region, req.realm, req.name, seasonId).bind().rating
            }) {
                if (it is NotFound) req to 0.0 else raise(it)
            }
        }

    private fun payload(guildRoster: GuildRoster, guild: WowEntityRequest): WowRealmGuild =
        WowRealmGuild(
            guild.name.lowercase(),
            guild.realm.lowercase(),
            guild.region.lowercase(),
            guildRoster.guildId
        )

    suspend fun resolveRoster(
        region: String,
        realm: String,
        name: String
    ): Pair<GuildRoster, List<WowEntityRequest>> {
        val roster = blizzardClient.getRetailGuildRoster(region, realm, name).getOrElse {
            if (it is NotFound) error("Guild $name ($region/$realm) not found") else error("Third party call failed: $it")
        }

        val members = roster.members
            .asSequence()
            .filter { it.level >= MAX_CHARACTER_LEVEL }
            .map {
                WowEntityRequest(
                    it.name,
                    region,
                    it.realmSlug ?: realm,
                    it.id
                )
            }
            .toList()

        return Pair(roster, members)
    }
}

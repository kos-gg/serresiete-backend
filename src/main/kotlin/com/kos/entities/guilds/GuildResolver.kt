package com.kos.entities.guilds

import com.kos.entities.domain.Entity
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.InsertEntityRequest
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.getCurrentAndNewMembers
import com.kos.views.Game

data class GuildWithRoster<G : Guild, M : InsertEntityRequest>(val guild: G, val members: List<M>)

data class ResolvedGuild<G : Guild, M : InsertEntityRequest>(
    val guild: G,
    val newMembers: List<InsertEntityRequest>,
    val existing: List<Pair<M, Entity>>
)

interface GuildResolver<G : Guild, M : InsertEntityRequest> {
    val game: Game
    val entitiesRepository: EntitiesRepository

    suspend fun resolveGuild(guild: EntityRequest): GuildWithRoster<G, M>

    suspend fun resolveRoster(newMembers: List<M>): List<InsertEntityRequest>

    fun hasGuildMemberChanged(member: M, tracked: Entity): Boolean

    suspend fun resolve(guild: EntityRequest): ResolvedGuild<G, M> {
        val (resolvedGuild, members) = resolveGuild(guild)
        val (newMembers, existing) = entitiesRepository.getCurrentAndNewMembers(members, game)
        return ResolvedGuild(resolvedGuild, resolveRoster(newMembers), existing)
    }
}

package com.kos.entities

import com.kos.entities.domain.Entity
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.InsertEntityRequest
import com.kos.entities.domain.WowGuild
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game

data class GuildMembers<G : WowGuild, M : InsertEntityRequest>(val guild: G, val members: List<M>)

data class ResolvedGuild<G : WowGuild>(
    val guild: G,
    val newMembers: List<Pair<InsertEntityRequest, String?>>,
    val existing: List<Entity>
)

interface GuildResolver<G : WowGuild, M : InsertEntityRequest> {
    val game: Game
    val entitiesRepository: EntitiesRepository

    suspend fun fetchRoster(guild: EntityRequest): GuildMembers<G, M>

    suspend fun admit(newMembers: List<M>): List<Pair<InsertEntityRequest, String?>>

    suspend fun resolve(guild: EntityRequest): ResolvedGuild<G> {
        val (resolvedGuild, members) = fetchRoster(guild)
        val lookups = members.map { member -> member to entitiesRepository.get(member, game) }

        return ResolvedGuild(
            guild = resolvedGuild,
            newMembers = admit(lookups.filter { (_, tracked) -> tracked == null }.map { (member, _) -> member }),
            existing = lookups.mapNotNull { (_, tracked) -> tracked }
        )
    }
}

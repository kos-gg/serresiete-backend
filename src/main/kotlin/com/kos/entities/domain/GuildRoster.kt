package com.kos.entities.domain

data class GuildRoster(
    val guildId: Long,
    val members: List<GuildRosterMember>
)

data class GuildRosterMember(
    val name: String,
    val level: Int,
    val id: Long?,
    val realmSlug: String?
)

data class WowRealm(val category: String)

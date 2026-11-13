package com.kos.entities

import com.kos.clients.ClientError
import com.kos.entities.domain.WowGuild
import com.kos.entities.domain.realmOrNull

sealed class PartialFailure {
    abstract fun error(): String
}

data class ThirdPartyCallFailed(
    val entityId: Long,
    val cause: ClientError
) : PartialFailure() {
    override fun error(): String = "Entity $entityId: $cause"
}

data class GuildUpdateFailed(
    val guild: WowGuild,
    val cause: Throwable
) : PartialFailure() {
    override fun error(): String =
        "Couldn't update guild ${guild.name} (${listOfNotNull(guild.region, guild.realmOrNull()).joinToString("/")}): ${cause.stackTraceToString()}"
}

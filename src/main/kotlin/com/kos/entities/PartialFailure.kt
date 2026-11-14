package com.kos.entities

import com.kos.entities.guilds.realmOrNull
import com.kos.entities.guilds.Guild
import com.kos.clients.ClientError

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
    val guild: Guild,
    val cause: Throwable
) : PartialFailure() {
    override fun error(): String =
        "Couldn't update guild ${guild.name} (${listOfNotNull(guild.region, guild.realmOrNull()).joinToString("/")}): ${cause.stackTraceToString()}"
}

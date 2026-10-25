package com.kos.entities

import com.kos.clients.ClientError
import com.kos.entities.domain.GuildPayload

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
    val guild: GuildPayload,
    val cause: Throwable
) : PartialFailure() {
    override fun error(): String =
        "Couldn't update guild ${guild.name} (${guild.region}/${guild.realm}): ${cause.stackTraceToString()}"
}

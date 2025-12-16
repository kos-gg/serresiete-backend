package com.kos.common.error

sealed class ServiceError

data class SyncProcessingError(val type: String, val message: String) : ServiceError()

data class WowHardcoreCharacterIsDead(val character: String, val characterId: Long) : ServiceError() {
    fun error(): String =
        "Character with name [$character] and id [$characterId] could not be sync because it is dead"
}
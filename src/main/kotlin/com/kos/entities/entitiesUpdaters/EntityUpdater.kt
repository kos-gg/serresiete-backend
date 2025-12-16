package com.kos.entities.entitiesUpdaters

import com.kos.common.error.ControllerError

interface EntityUpdater<A> {
    suspend fun update(entities: List<A>): List<ControllerError>
}
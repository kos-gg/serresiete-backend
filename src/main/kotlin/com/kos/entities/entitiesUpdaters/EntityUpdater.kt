package com.kos.entities.entitiesUpdaters

import com.kos.common.ControllerError

interface EntityUpdater<A> {
    suspend fun update(entities: List<A>): List<ControllerError>
}
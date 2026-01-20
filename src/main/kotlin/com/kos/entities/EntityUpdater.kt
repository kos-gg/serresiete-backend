package com.kos.entities

import com.kos.common.error.ServiceError

interface EntityUpdater<A> {
    suspend fun update(entities: List<A>): List<ServiceError>
}
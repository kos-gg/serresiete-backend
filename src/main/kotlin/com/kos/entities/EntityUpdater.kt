package com.kos.entities


interface EntityUpdater<A> {
    suspend fun update(entities: List<A>): List<PartialFailure>
}
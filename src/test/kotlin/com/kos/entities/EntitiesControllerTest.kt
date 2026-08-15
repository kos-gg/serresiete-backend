package com.kos.entities

import arrow.core.Either
import com.kos.activities.Activities
import com.kos.common.error.EntityError
import com.kos.common.error.NotAuthorized
import com.kos.common.error.NotEnoughPermissions
import com.kos.common.error.ResolverNotFound
import com.kos.datacache.DataCacheService
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.views.Game
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import kotlin.test.Test
import kotlin.test.assertEquals

class EntitiesControllerTest {
    private val dataCacheService = mock(DataCacheService::class.java)
    private val entitiesService = mock(EntitiesService::class.java)
    private val controller = EntitiesController(dataCacheService, entitiesService)

    @Test
    fun `exists returns not authorized for an anonymous client`() {
        runBlocking {
            val result = controller.exists(null, setOf(Activities.checkEntitiesExist), listOf(basicWowRequest), Game.WOW)
            assertEquals(Either.Left(NotAuthorized), result)
        }
    }

    @Test
    fun `exists returns not enough permissions when the client lacks the activity`() {
        runBlocking {
            val result = controller.exists("client", setOf(), listOf(basicWowRequest), Game.WOW)
            assertEquals(Either.Left(NotEnoughPermissions("client")), result)
        }
    }

    @Test
    fun `exists delegates to the service and returns its result`() {
        runBlocking {
            `when`(entitiesService.exists(listOf(basicWowRequest), Game.WOW))
                .thenReturn(Either.Right(listOf(basicWowRequest)))

            val result = controller.exists(
                "client",
                setOf(Activities.checkEntitiesExist),
                listOf(basicWowRequest),
                Game.WOW
            )

            assertEquals(Either.Right(listOf(basicWowRequest)), result)
        }
    }

    @Test
    fun `exists maps a service error into a controller error`() {
        runBlocking {
            `when`(entitiesService.exists(listOf(basicWowRequest), Game.WOW))
                .thenReturn(Either.Left(ResolverNotFound(Game.WOW)))

            val result = controller.exists(
                "client",
                setOf(Activities.checkEntitiesExist),
                listOf(basicWowRequest),
                Game.WOW
            )

            assertEquals(Either.Left(EntityError(ResolverNotFound(Game.WOW).error())), result)
        }
    }
}

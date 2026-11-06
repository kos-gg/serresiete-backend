package com.kos.entities

import arrow.core.Either
import com.kos.activities.Activities
import com.kos.common.error.BadRequest
import com.kos.common.error.EntitiesDoNotMatchGame
import com.kos.common.error.InvalidRegion
import com.kos.common.error.NotAuthorized
import com.kos.common.error.NotEnoughPermissions
import com.kos.datacache.DataCacheService
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.entities.domain.EntitiesExistResponse
import com.kos.entities.domain.GuildExistsResponse
import com.kos.entities.domain.GuildPayload
import com.kos.views.Game
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntitiesControllerTest {
    private val dataCacheService = mock(DataCacheService::class.java)
    private val entitiesService = mock(EntitiesService::class.java)
    private val controller = EntitiesController(dataCacheService, entitiesService)

    @Test
    fun `exists returns not authorized for an anonymous client`() {
        runBlocking {
            val result =
                controller.exists(null, setOf(Activities.checkEntitiesExist), listOf(basicWowRequest), Game.WOW)
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
    fun `exists rejects entities of another game without calling the service`() {
        runBlocking {
            val result = controller.exists(
                "client",
                setOf(Activities.checkEntitiesExist),
                listOf(basicWowRequest),
                Game.LOL
            )

            assertEquals(Either.Left(EntitiesDoNotMatchGame), result)
            verifyNoInteractions(entitiesService)
        }
    }

    @Test
    fun `exists rejects entities outside the blizzard regions without calling the service`() {
        runBlocking {
            val result = controller.exists(
                "client",
                setOf(Activities.checkEntitiesExist),
                listOf(basicWowRequest.copy(region = "attacker.com#")),
                Game.WOW
            )

            assertEquals(Either.Left(InvalidRegion), result)
            verifyNoInteractions(entitiesService)
        }
    }

    @Test
    fun `guildExists rejects a region outside the blizzard regions without calling the service`() {
        runBlocking {
            val result = controller.guildExists(
                "client",
                setOf(Activities.checkEntitiesExist),
                "Method",
                "attacker.com#",
                "twisting-nether"
            )

            assertEquals(Either.Left(InvalidRegion), result)
            verifyNoInteractions(entitiesService)
        }
    }

    @Test
    fun `searching an entity outside the blizzard regions is a bad request`() {
        val result = parametersToEntityRequest(
            parametersOf(
                "game" to listOf("wow_forever"),
                "name" to listOf("sanxei"),
                "surname" to listOf("tarde"),
                "region" to listOf("attacker.com#")
            )
        )

        assertTrue(result.isLeft { it is BadRequest })
    }

    @Test
    fun `searching an entity accepts a blizzard region regardless of case`() {
        val result = parametersToEntityRequest(
            parametersOf(
                "game" to listOf("wow"),
                "name" to listOf("sanxei"),
                "realm" to listOf("zuljin"),
                "region" to listOf("EU")
            )
        )

        assertTrue(result.isRight())
    }

    @Test
    fun `exists delegates to the service and returns its result`() {
        runBlocking {
            val response = EntitiesExistResponse(
                exist = listOf(basicWowRequest.toResponse()),
                nonExisting = listOf(),
                unchecked = listOf()
            )
            `when`(entitiesService.exists(listOf(basicWowRequest), Game.WOW))
                .thenReturn(response)

            val result = controller.exists(
                "client",
                setOf(Activities.checkEntitiesExist),
                listOf(basicWowRequest),
                Game.WOW
            )

            assertEquals(Either.Right(response), result)
        }
    }

    @Test
    fun `guildExists returns not authorized for an anonymous client`() {
        runBlocking {
            val result =
                controller.guildExists(null, setOf(Activities.checkEntitiesExist), "Method", "eu", "twisting-nether")
            assertEquals(Either.Left(NotAuthorized), result)
        }
    }

    @Test
    fun `guildExists returns not enough permissions when the client lacks the activity`() {
        runBlocking {
            val result = controller.guildExists("client", setOf(), "Method", "eu", "twisting-nether")
            assertEquals(Either.Left(NotEnoughPermissions("client")), result)
        }
    }

    @Test
    fun `guildExists delegates to the service and returns its result`() {
        runBlocking {
            val response = GuildExistsResponse(GuildPayload("method", "twisting-nether", "eu", 999))
            `when`(entitiesService.guildExists("Method", "eu", "twisting-nether"))
                .thenReturn(Either.Right(response))

            val result = controller.guildExists(
                "client",
                setOf(Activities.checkEntitiesExist),
                "Method",
                "eu",
                "twisting-nether"
            )

            assertEquals(Either.Right(response), result)
        }
    }

}

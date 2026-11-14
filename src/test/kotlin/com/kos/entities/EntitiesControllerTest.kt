package com.kos.entities

import com.kos.entities.guilds.GuildExistsResponse
import com.kos.entities.guilds.WowForeverGuild
import com.kos.entities.guilds.WowGuild
import arrow.core.Either
import com.kos.activities.Activities
import com.kos.common.error.BadRequest
import com.kos.common.error.EntitiesDoNotMatchGame
import com.kos.common.error.InvalidRegion
import com.kos.common.error.MissingSurname
import com.kos.common.error.NotAuthorized
import com.kos.common.error.NotEnoughPermissions
import com.kos.datacache.DataCacheService
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.entities.domain.EntitiesExistResponse
import com.kos.entities.domain.WowForeverEntityRequest
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
                "twisting-nether",
                Game.WOW
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
    fun `searching an entity normalizes its name to lowercase`() {
        val result = parametersToEntityRequest(
            parametersOf(
                "game" to listOf("wow_forever"),
                "name" to listOf("SIEMPRE"),
                "surname" to listOf("tardE"),
                "region" to listOf("eu")
            )
        )

        assertEquals(Either.Right(WowForeverEntityRequest("siempre", "tarde", "eu") to Game.WOW_FOREVER), result)
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
                controller.guildExists(null, setOf(Activities.checkEntitiesExist), "Method", "eu", "twisting-nether", Game.WOW)
            assertEquals(Either.Left(NotAuthorized), result)
        }
    }

    @Test
    fun `guildExists returns not enough permissions when the client lacks the activity`() {
        runBlocking {
            val result = controller.guildExists("client", setOf(), "Method", "eu", "twisting-nether", Game.WOW)
            assertEquals(Either.Left(NotEnoughPermissions("client")), result)
        }
    }

    @Test
    fun `guildExists delegates to the service and returns its result`() {
        runBlocking {
            val response = GuildExistsResponse(WowGuild("method", "twisting-nether", "eu", 999))
            `when`(entitiesService.wowGuildExists("Method", "eu", "twisting-nether"))
                .thenReturn(Either.Right(response))

            val result = controller.guildExists(
                "client",
                setOf(Activities.checkEntitiesExist),
                "Method",
                "eu",
                "twisting-nether",
                Game.WOW
            )

            assertEquals(Either.Right(response), result)
        }
    }

    @Test
    fun `exists rejects a wow forever character without surname without calling the service`() {
        runBlocking {
            val result = controller.exists(
                "client",
                setOf(Activities.checkEntitiesExist),
                listOf(WowForeverEntityRequest(name = "sanxei", region = "eu")),
                Game.WOW_FOREVER
            )

            assertEquals(Either.Left(MissingSurname), result)
            verifyNoInteractions(entitiesService)
        }
    }

    @Test
    fun `guildExists checks a wow forever guild without realm`() {
        runBlocking {
            val response = GuildExistsResponse(WowForeverGuild("tsalab", "eu", 104078))
            `when`(entitiesService.wowForeverGuildExists("tsalab", "eu"))
                .thenReturn(Either.Right(response))

            val result = controller.guildExists(
                "client",
                setOf(Activities.checkEntitiesExist),
                "tsalab",
                "eu",
                null,
                Game.WOW_FOREVER
            )

            assertEquals(Either.Right(response), result)
        }
    }

    @Test
    fun `guildExists rejects a wow guild without realm without calling the service`() {
        runBlocking {
            val result = controller.guildExists("client", setOf(Activities.checkEntitiesExist), "Method", "eu", null, Game.WOW)

            assertTrue(result.isLeft { it is BadRequest })
            verifyNoInteractions(entitiesService)
        }
    }

    @Test
    fun `guildExists rejects a wow forever guild with a realm without calling the service`() {
        runBlocking {
            val result = controller.guildExists(
                "client", setOf(Activities.checkEntitiesExist), "tsalab", "eu", "twisting-nether", Game.WOW_FOREVER
            )

            assertTrue(result.isLeft { it is BadRequest })
            verifyNoInteractions(entitiesService)
        }
    }

    @Test
    fun `guildExists rejects games without guild existence checks without calling the service`() {
        runBlocking {
            val result = controller.guildExists(
                "client", setOf(Activities.checkEntitiesExist), "Method", "eu", "twisting-nether", Game.WOW_HC
            )

            assertTrue(result.isLeft { it is BadRequest })
            verifyNoInteractions(entitiesService)
        }
    }

    @Test
    fun `guildExists rejects a wow forever guild outside the blizzard regions without calling the service`() {
        runBlocking {
            val result = controller.guildExists(
                "client", setOf(Activities.checkEntitiesExist), "tsalab", "attacker.com#", null, Game.WOW_FOREVER
            )

            assertEquals(Either.Left(InvalidRegion), result)
            verifyNoInteractions(entitiesService)
        }
    }
}

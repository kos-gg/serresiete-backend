package com.kos.tasks.runners

import arrow.core.Either
import com.kos.clients.NotFound
import com.kos.clients.blizzard.BlizzardClient
import com.kos.entities.EntitiesService
import com.kos.entities.EntityResolverProvider
import com.kos.entities.domain.GuildPayload
import com.kos.entities.domain.WowForeverEntity
import com.kos.entities.domain.WowForeverGuildMember
import com.kos.entities.domain.WowForeverGuildRoster
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.entities.repository.wowguilds.WowGuildsState
import com.kos.sources.wowforever.WowForeverGuildResolver
import com.kos.sources.wowforever.WowForeverGuildUpdater
import com.kos.tasks.TaskCompleted
import com.kos.views.Game
import com.kos.views.repository.ViewsInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateWowForeverGuildsTaskRunnerTest {

    private val blizzardClient = mock(BlizzardClient::class.java)
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val wowGuildsRepository = WowGuildsInMemoryRepository()
    private val viewsRepository = ViewsInMemoryRepository()
    private val wowForeverGuildResolver = WowForeverGuildResolver(entitiesRepository, blizzardClient)
    private val entitiesService = EntitiesService(
        entitiesRepository,
        wowGuildsRepository,
        EntityResolverProvider(
            wowResolver = mock(),
            wowGuildResolver = mock(),
            wowHardcoreResolver = mock(),
            wowHardcoreGuildResolver = mock(),
            lolResolver = mock(),
            wowForeverResolver = mock(),
            wowForeverGuildResolver = wowForeverGuildResolver
        ),
        mock(),
        mock(),
        mock(),
        mock(),
        wowForeverGuildResolver,
        WowForeverGuildUpdater(wowForeverGuildResolver, entitiesRepository, viewsRepository)
    )
    private val runner = UpdateWowForeverGuildsTaskRunner(entitiesService)

    private val region = "eu"
    private val guild = "tsalab"
    private val viewId = "1"

    private suspend fun guildViewExists() {
        viewsRepository.create(viewId, "guild view", "sanxei", listOf(), Game.WOW_FOREVER, false, null)
        wowGuildsRepository.withState(
            WowGuildsState(listOf(Triple(GuildPayload(guild, "", region, 104078), viewId, Game.WOW_FOREVER)))
        )
    }

    @Test
    fun `wow forever guilds are updated and task is recorded as successful`() = runBlocking {
        guildViewExists()
        `when`(blizzardClient.getForeverGuildRoster(region, guild))
            .thenReturn(Either.Right(WowForeverGuildRoster(104078, listOf(WowForeverGuildMember(1, "Sanxei", "Zugzug")))))

        val result = runner.run(null)

        assertEquals(Either.Right(TaskCompleted()), result)
        val member = entitiesRepository.get(Game.WOW_FOREVER).single() as WowForeverEntity
        assertEquals(1, member.blizzardId)
        assertEquals(listOf(member.id), viewsRepository.get(viewId)!!.entitiesIds)
    }

    @Test
    fun `task fails when a guild can't be updated`() = runBlocking {
        guildViewExists()
        `when`(blizzardClient.getForeverGuildRoster(region, guild)).thenReturn(Either.Left(NotFound))

        val result = runner.run(null)

        assertTrue(result.isLeft())
    }
}

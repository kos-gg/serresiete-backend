package com.kos.tasks.runners

import com.kos.entities.guilds.repository.WowGuildsState
import com.kos.entities.guilds.repository.WowGuildsInMemoryRepository
import com.kos.entities.guilds.WowGuild
import com.kos.tasks.TaskCompleted
import arrow.core.Either
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.blizzard.toGuildRoster
import com.kos.clients.blizzard.toWowCharacter
import com.kos.clients.blizzard.toWowRealm
import com.kos.clients.domain.*
import com.kos.datacache.BlizzardMockHelper.getWowCharacterResponse
import com.kos.entities.EntitiesService
import com.kos.entities.EntityResolverProvider
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.sources.wowhc.WowHardcoreEntityResolver
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.views.Game
import com.kos.views.repository.ViewsInMemoryRepository
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import com.kos.sources.wowhc.WowHardcoreGuildResolver

class UpdateWowHardcoreGuildsTaskRunnerTest {

    private val blizzardClient = mock(BlizzardClient::class.java)
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val wowGuildsRepository = WowGuildsInMemoryRepository()
    private val viewsRepository = ViewsInMemoryRepository()
    private val wowHardcoreResolver = WowHardcoreEntityResolver(entitiesRepository, blizzardClient)
    private val wowHardcoreGuildResolver = WowHardcoreGuildResolver(entitiesRepository, blizzardClient, wowHardcoreResolver)
    private val entitiesService = EntitiesService(
        entitiesRepository,
        wowGuildsRepository,
        EntityResolverProvider(wowResolver = mock(), wowGuildResolver = mock(), wowHardcoreResolver = wowHardcoreResolver, wowHardcoreGuildResolver = wowHardcoreGuildResolver, lolResolver = mock(), wowForeverResolver = mock(), wowForeverGuildResolver = mock()),
        mock(),
        mock(),
        WowHardcoreGuildUpdater(wowHardcoreGuildResolver, wowHardcoreResolver, entitiesRepository, viewsRepository),
        mock(),
        mock(),
        mock()
    )
    private val runner = UpdateWowHardcoreGuildsTaskRunner(entitiesService)

    @Test
    fun `wow hardcore guilds are updated and task is recorded as successful`() = runBlocking {
        val realm = "Soulseeker"
        val guild = "Balast"
        val region = "Nolase"
        val blizzardId: Long = 1
        val character = "Surmana"

        wowGuildsRepository.withState(
            WowGuildsState(listOf(Triple(WowGuild(guild, realm, region, blizzardId), "1", Game.WOW_HC)))
        )
        `when`(blizzardClient.getHardcoreGuildRoster(region, realm, guild))
            .thenReturn(
                Either.Right(
                    GetWowRosterResponse(
                        listOf(WowMemberResponse(WowCharacterResponse(character, 60))),
                        WowGuildResponse(blizzardId)
                    ).toGuildRoster()
                )
            )
        `when`(blizzardClient.getClassicProfile(region, realm, character.toLowerCasePreservingASCIIRules()))
            .thenReturn(Either.Right(getWowCharacterResponse.copy(name = character, guild = guild).toWowCharacter()))
        `when`(blizzardClient.getRealm(region, 5220))
            .thenReturn(Either.Right(GetWowRealmResponse("Hardcore").toWowRealm()))

        val result = runner.run(null)

        assertEquals(Either.Right(TaskCompleted()), result)
    }
}

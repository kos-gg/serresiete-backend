package com.kos.sources.lol

import arrow.core.Either
import com.kos.clients.HttpError
import com.kos.clients.domain.GetPUUIDResponse
import com.kos.clients.domain.GetSummonerResponse
import com.kos.clients.riot.RiotClient
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.lolEntityRequest
import com.kos.entities.domain.LolEntityRequest
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class LolEntityResolverTest {
    private val riotClient = mock(RiotClient::class.java)

    @Test
    fun `resolves a new character found in riot`() {
        runBlocking {
            `when`(riotClient.getPUUIDByRiotId(lolEntityRequest.name, lolEntityRequest.tag))
                .thenReturn(Either.Right(GetPUUIDResponse("1", lolEntityRequest.name, lolEntityRequest.tag)))
            `when`(riotClient.getSummonerByPuuid("1"))
                .thenReturn(Either.Right(GetSummonerResponse("1", 5, 10L, 100)))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = LolEntityResolver(repo, riotClient)

            resolver.resolve(listOf(lolEntityRequest), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(1, res.entities.size)
                    assertEquals(lolEntityRequest.name, res.entities.first().first.name)
                }
        }
    }

    @Test
    fun `a character not found in riot is dropped instead of failing the whole batch`() {
        runBlocking {
            `when`(riotClient.getPUUIDByRiotId(lolEntityRequest.name, lolEntityRequest.tag))
                .thenReturn(Either.Left(HttpError(404, "not found")))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = LolEntityResolver(repo, riotClient)

            resolver.resolve(listOf(lolEntityRequest), null)
                .onLeft { fail() }
                .onRight { res -> assertEquals(listOf(), res.entities) }
        }
    }

    @Test
    fun `a character already in the repository is returned as existing without calling riot`() {
        runBlocking {
            val repo = EntitiesInMemoryRepository()
                .withState(EntitiesState(listOf(), listOf(), listOf(basicLolEntity)))
            val resolver = LolEntityResolver(repo, riotClient)

            val request = LolEntityRequest(basicLolEntity.name, basicLolEntity.tag)

            resolver.resolve(listOf(request), null)
                .onLeft { fail() }
                .onRight { res -> assertEquals(listOf(basicLolEntity to null), res.existing) }

            verifyNoInteractions(riotClient)
        }
    }
}

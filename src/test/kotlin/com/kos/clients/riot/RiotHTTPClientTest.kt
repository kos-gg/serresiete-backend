package com.kos.clients.riot

import com.kos.assertTrue
import com.kos.clients.domain.QueueType
import com.kos.clients.riot.RiotHTTPClientHelper.client
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

//TODO: Añadir test sobre las excepciones

class RiotHTTPClientTest {
    private val riotHTTPClient = RiotHTTPClient(client, "fake-key")

    @Test
    fun `get puuid by riot id works as expected`() {
        runBlocking {
            val res = riotHTTPClient.getPUUIDByRiotId("GTP ZeroMVPs", "WOW")
            assertTrue(res.isRight())
        }
    }

    @Test
    fun `get summoner by puuid works as expected`() {
        runBlocking {
            val res = riotHTTPClient.getSummonerByPuuid("vJre0esG5sIx3rvCAe-YVsDfqCIMV5b2P-61wrYZ4w-hs9u_Ek8dVlo-KLo-GNA4NumLV1YTNxeCmA")
            assertTrue(res.isRight())

        }
    }

    @Test
    fun `get matches by puuid works as expected`() {
        runBlocking {
            val res = riotHTTPClient.getMatchesByPuuid("vJre0esG5sIx3rvCAe-YVsDfqCIMV5b2P-61wrYZ4w-hs9u_Ek8dVlo-KLo-GNA4NumLV1YTNxeCmA",
                QueueType.SOLO_Q.toInt()
            )
            assertTrue(res.isRight())

        }
    }

    @Test
    fun `get match by id works as expected`() {
        runBlocking {
            val res = riotHTTPClient.getMatchById("EUW1_7130322326")

            assertTrue(res.isRight())
        }
    }

    @Test
    fun `get league entries by summoner id works as expected`() {
        runBlocking {
            val res = riotHTTPClient.getLeagueEntriesByPUUID("XpUAakpMee4budbZ_KVchTTxwkN4OHgqjbYa0r4pXR_Ya6E")

            assertTrue(res.isRight())
        }
    }

    @Test
    fun `get account by puuid works as expected`() {
        runBlocking {
            val res = riotHTTPClient.getAccountByPUUID("XpUAakpMee4budbZ_KVchTTxwkN4OHgqjbYa0r4pXR_Ya6E")

            assertTrue(res.isRight())
        }
    }
}
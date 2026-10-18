package com.kos.clients.riot

import com.kos.clients.domain.GetMatchResponse
import com.kos.clients.domain.LeagueEntryResponse
import com.kos.clients.domain.MatchInfo
import com.kos.clients.domain.Metadata
import com.kos.datacache.domain.QueueType
import kotlin.test.Test
import kotlin.test.assertEquals
import com.kos.sources.lol.LeagueEntry

class RiotMappersTest {

    private fun leagueEntryResponse(queueType: String) =
        LeagueEntryResponse(queueType, "GOLD", "I", 1, 13, 14, false)

    @Test
    fun `league entries keep known queues and drop unknown ones`() {
        val entries = listOf(
            leagueEntryResponse("RANKED_SOLO_5x5"),
            leagueEntryResponse("RANKED_FLEX_SR"),
            leagueEntryResponse("CHERRY")
        ).toLeagueEntries()

        assertEquals(
            listOf(
                LeagueEntry(QueueType.SOLO_Q, "GOLD", "I", 1, 13, 14),
                LeagueEntry(QueueType.FLEX_Q, "GOLD", "I", 1, 13, 14)
            ),
            entries
        )
    }

    @Test
    fun `queue types map to riot queue ids`() {
        assertEquals(420, QueueType.SOLO_Q.toRiotQueueId())
        assertEquals(440, QueueType.FLEX_Q.toRiotQueueId())
    }

    @Test
    fun `a match is finished correctly only when riot reports GameComplete`() {
        fun match(endOfGameResult: String) =
            GetMatchResponse(MatchInfo(endOfGameResult, 1563, 11, listOf()), Metadata("EUW1_1")).toLolMatch()

        assertEquals(true, match("GameComplete").finishedCorrectly)
        assertEquals(false, match("Abort_Unexpected").finishedCorrectly)
    }
}

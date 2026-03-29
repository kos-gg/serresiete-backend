package com.kos.clients.raiderio

import com.kos.clients.domain.MythicPlusRun
import com.kos.clients.raiderio.RaiderIoHttpClientHelper.mythicPlusRun
import com.kos.clients.raiderio.RaiderIoHttpClientHelper.mythicPlusRunJson
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RaiderIoDomainTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `i can deserialize a mythic plus run`() {
        assertEquals(
            mythicPlusRun,
            json.decodeFromString<MythicPlusRun>(mythicPlusRunJson)
        )
    }
}

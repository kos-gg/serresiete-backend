package com.kos.clients.blizzard

import com.kos.clients.domain.*
import com.kos.datacache.BlizzardMockHelper.getWowCharacterResponse
import com.kos.datacache.BlizzardMockHelper.getWowCharacterResponseString
import com.kos.datacache.BlizzardMockHelper.getWowEquipmentResponse
import com.kos.datacache.BlizzardMockHelper.getWowEquipmentResponseString
import com.kos.datacache.BlizzardMockHelper.getWowItemResponse
import com.kos.datacache.BlizzardMockHelper.getWowItemResponseString
import com.kos.datacache.BlizzardMockHelper.getWowSpecializationsResponse
import com.kos.datacache.BlizzardMockHelper.getWowSpecializationsResponseString
import com.kos.datacache.BlizzardMockHelper.getWowStatsResponse
import com.kos.datacache.BlizzardMockHelper.getWowStatsResponseString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

class BlizzardDomainTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `i can deserialize a character response`() {
        assertEquals(
            getWowCharacterResponse,
            json.decodeFromString<GetWowCharacterResponse>(getWowCharacterResponseString)
        )
    }

    @Test
    fun `i can deserialize a character equipment response`() {
        assertEquals(
            getWowEquipmentResponse,
            json.decodeFromString<GetWowEquipmentResponse>(getWowEquipmentResponseString)
        )
    }

    @Test
    fun `i can deserialize a character stats response`() {
        assertEquals(
            getWowStatsResponse,
            json.decodeFromString<GetWowCharacterStatsResponse>(getWowStatsResponseString)
        )
    }

    @Test
    fun `i can deserialize a character specializations response`() {
        assertEquals(
            getWowSpecializationsResponse,
            json.decodeFromString<GetWowSpecializationsResponse>(getWowSpecializationsResponseString)
        )
    }

    @Test
    fun `i can deserialize an item response`() {
        assertEquals(getWowItemResponse, json.decodeFromString<GetWowItemResponse>(getWowItemResponseString))
    }
}
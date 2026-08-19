package com.kos.entities.domain

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class EntityResponseSerializerTest {
    private val json = Json

    @Test
    fun `encodes a WowEntityResponse with its own fields and no type discriminator`() {
        val encoded = json.encodeToString<EntityResponse>(WowEntityResponse("kakarona", "eu", "zuljin"))

        assertEquals("""{"name":"kakarona","region":"eu","realm":"zuljin"}""", encoded)
        assertFalse("type" in encoded)
    }

    @Test
    fun `encodes a LolEntityResponse with its own fields and no type discriminator`() {
        val encoded = json.encodeToString<EntityResponse>(LolEntityResponse("sanxei", "EUW"))

        assertEquals("""{"name":"sanxei","tag":"EUW"}""", encoded)
        assertFalse("type" in encoded)
    }

    @Test
    fun `decodes a wow-shaped object by the presence of region and realm`() {
        val decoded = json.decodeFromString<EntityResponse>("""{"name":"kakarona","region":"eu","realm":"zuljin"}""")

        assertEquals(WowEntityResponse("kakarona", "eu", "zuljin"), decoded)
    }

    @Test
    fun `decodes a lol-shaped object by the presence of tag`() {
        val decoded = json.decodeFromString<EntityResponse>("""{"name":"sanxei","tag":"EUW"}""")

        assertEquals(LolEntityResponse("sanxei", "EUW"), decoded)
    }

    @Test
    fun `round trips a mixed list inside EntitiesExistResponse`() {
        val response = EntitiesExistResponse(
            exist = listOf(WowEntityResponse("kakarona", "eu", "zuljin")),
            nonExisting = listOf(LolEntityResponse("sanxei", "EUW"))
        )

        val decoded = json.decodeFromString<EntitiesExistResponse>(json.encodeToString(response))

        assertEquals(response, decoded)
    }

    @Test
    fun `fails to decode an object that matches neither shape`() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<EntityResponse>("""{"name":"unknown"}""")
        }
    }
}

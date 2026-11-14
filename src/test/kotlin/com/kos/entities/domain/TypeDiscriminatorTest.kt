package com.kos.entities.domain

import com.kos.entities.guilds.GuildExistsResponse
import com.kos.entities.guilds.WowForeverGuild
import com.kos.entities.guilds.WowGuild
import com.kos.views.ViewExtraArguments
import com.kos.views.WowExtraArguments
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeDiscriminatorTest {
    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(EntityRequest::class) {
                subclass(LolEntityRequest::class, LolEntityRequest.serializer())
                subclass(WowEntityRequest::class, WowEntityRequest.serializer())
            }
        }
    }

    private fun typeOf(encoded: String) = json.parseToJsonElement(encoded).jsonObject["type"]?.jsonPrimitive?.content

    @Test
    fun `entity requests are discriminated by game`() {
        assertEquals("wow", typeOf(json.encodeToString<EntityRequest>(WowEntityRequest("kakarona", "eu", "zuljin"))))
        assertEquals("lol", typeOf(json.encodeToString<EntityRequest>(LolEntityRequest("sanxei", "EUW"))))
        assertEquals(
            LolEntityRequest("sanxei", "EUW"),
            json.decodeFromString<EntityRequest>("""{"type":"lol","name":"sanxei","tag":"EUW"}""")
        )
    }

    @Test
    fun `a wow forever request without surname is decoded with an empty surname`() {
        assertEquals(
            WowForeverEntityRequest(name = "tsalab", region = "eu"),
            json.decodeFromString<EntityRequest>("""{"type":"wow_forever","name":"tsalab","region":"eu"}""")
        )
    }

    @Test
    fun `entities are discriminated by game`() {
        assertEquals("wow", typeOf(json.encodeToString<Entity>(WowEntity(1, "kakarona", "eu", "zuljin", null))))
        assertEquals("lol", typeOf(json.encodeToString<Entity>(LolEntity(2, "sanxei", "EUW", "puuid", 1, 100))))
    }

    @Test
    fun `wow extra arguments are shared by wow and wow hardcore views`() {
        assertEquals("wow", typeOf(json.encodeToString<ViewExtraArguments>(WowExtraArguments(isGuild = true))))
    }

    @Test
    fun `guild exists responses tell wow guilds from wow forever guilds`() {
        assertEquals(
            """{"guild":{"type":"wow","name":"method","realm":"twisting-nether","region":"eu","blizzardId":1}}""",
            json.encodeToString(GuildExistsResponse(WowGuild("method", "twisting-nether", "eu", 1L)))
        )
        assertEquals(
            """{"guild":{"type":"wow_forever","name":"tsalab","region":"eu","blizzardId":104078}}""",
            json.encodeToString(GuildExistsResponse(WowForeverGuild("tsalab", "eu", 104078L)))
        )
    }
}

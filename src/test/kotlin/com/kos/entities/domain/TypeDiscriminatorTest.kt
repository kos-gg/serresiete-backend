package com.kos.entities.domain

import com.kos.views.GuildArgs
import com.kos.views.ViewExtraArguments
import com.kos.views.WowExtraArguments
import com.kos.views.WowHardcoreExtraArguments
import kotlinx.serialization.decodeFromString
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
    fun `entities are discriminated by game`() {
        assertEquals("wow", typeOf(json.encodeToString<Entity>(WowEntity(1, "kakarona", "eu", "zuljin", null))))
        assertEquals("lol", typeOf(json.encodeToString<Entity>(LolEntity(2, "sanxei", "EUW", "puuid", 1, 100))))
    }

    @Test
    fun `view extra arguments are discriminated by game`() {
        assertEquals(
            "wow",
            typeOf(json.encodeToString<ViewExtraArguments>(WowExtraArguments(guild = GuildArgs.RESOLVE)))
        )
        assertEquals("wow_hc", typeOf(json.encodeToString<ViewExtraArguments>(WowHardcoreExtraArguments(true))))
    }
}

package com.kos.plugins

import com.kos.entities.CreateEntityRequest
import com.kos.entities.LolEntityRequest
import com.kos.entities.WowEntityRequest
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic


fun Application.configureSerialization() {
    val sm = SerializersModule {
        polymorphic(CreateEntityRequest::class) {
            subclass(LolEntityRequest::class, LolEntityRequest.serializer())
            subclass(WowEntityRequest::class, WowEntityRequest.serializer())
        }
    }

    install(ContentNegotiation) {
        json(
            json = Json {
                serializersModule = sm
                classDiscriminator = "type"
            }
        )
    }
}
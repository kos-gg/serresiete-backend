package com.kos.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCors() {
    val allowedOrigins = (System.getenv("ALLOWED_ORIGIN") ?: "http://localhost:5173")
        .split(",")
        .map { it.trim() }
    install(CORS) {
        allowedOrigins.forEach { origin ->
            val (scheme, host) = origin.split("://", limit = 2)
            allowHost(host, schemes = listOf(scheme))
        }
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        allowCredentials = true
    }
}
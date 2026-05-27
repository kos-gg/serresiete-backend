package com.kos.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCors() {
    val allowedOrigin = System.getenv("ALLOWED_ORIGIN") ?: "http://localhost:5173"
    val (scheme, host) = allowedOrigin.split("://", limit = 2)
    install(CORS) {
        allowHost(host, schemes = listOf(scheme))
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
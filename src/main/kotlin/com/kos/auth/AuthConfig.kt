package com.kos.auth

import com.kos.common.JWTConfig

data class AuthConfig(
    val jwtConfig: JWTConfig,
    val isHttps: Boolean = (System.getenv("ALLOWED_ORIGIN") ?: System.getenv("ALLOWED_ORIGIN_LOCAL"))
        ?.split(",")
        ?.any { it.trim().startsWith("https://") }
        ?: false
) {
    val sameSite: Map<String, String> = if (isHttps) mapOf("SameSite" to "None") else emptyMap()
}

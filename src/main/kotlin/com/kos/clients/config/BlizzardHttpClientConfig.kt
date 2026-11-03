package com.kos.clients.config

import com.kos.clients.blizzard.BlizzardAuthClient
import com.kos.clients.redactAccessKey
import io.github.resilience4j.kotlin.ratelimiter.RateLimiterConfig
import io.github.resilience4j.kotlin.ratelimiter.executeSuspendFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import org.slf4j.LoggerFactory
import java.time.Duration

private val logger = LoggerFactory.getLogger("blizzardClient")

fun HttpClientConfig<*>.blizzardHttpClientConfig(retryConfig: RetryConfig, blizzardAuthClient: BlizzardAuthClient) {
    val rateLimiter = RateLimiter.of(
        "blizzardRateLimiter",
        RateLimiterConfig {
            this.limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(1))
                .build()
        }
    )

    suspend fun requestToken(): BearerTokens? =
        blizzardAuthClient.getAccessToken().fold(
            ifLeft = {
                logger.warn("Could not obtain Blizzard token: {}", it)
                null
            },
            ifRight = { BearerTokens(it.accessToken, "") }
        )

    val BlizzardRateLimitPlugin = createClientPlugin("BlizzardRateLimit") {
        on(Send) { request -> rateLimiter.executeSuspendFunction { proceed(request) } }
    }

    install(HttpRequestRetry) { defaultRetry(retryConfig, logger, ::redactAccessKey) }
    install(Auth) {
        bearer {
            loadTokens { requestToken() }
            refreshTokens { requestToken() }
        }
    }
    install(BlizzardRateLimitPlugin)
    install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
}

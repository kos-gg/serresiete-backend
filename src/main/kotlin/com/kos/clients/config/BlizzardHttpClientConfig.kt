package com.kos.clients.config

import com.kos.clients.RetryConfig
import com.kos.clients.blizzard.BlizzardAuthClient
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
    val perSecondRateLimiter = RateLimiter.of(
        "perSecondLimiter",
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

    installRetry(retryConfig, logger)
    install(Auth) {
        bearer {
            loadTokens { requestToken() }
            refreshTokens { requestToken() }
        }
    }
    install(createClientPlugin("BlizzardRateLimit") {
        on(Send) { request -> perSecondRateLimiter.executeSuspendFunction { proceed(request) } }
    })
    install(HttpTimeout) {
        requestTimeoutMillis = 8_000
    }
}

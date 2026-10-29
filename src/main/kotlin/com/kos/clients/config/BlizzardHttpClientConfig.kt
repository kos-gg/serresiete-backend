package com.kos.clients.config

import com.kos.clients.RetryConfig
import com.kos.clients.blizzard.BlizzardAuthClient
import com.kos.common.WithLogger
import io.github.resilience4j.kotlin.ratelimiter.RateLimiterConfig
import io.github.resilience4j.kotlin.ratelimiter.executeSuspendFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.io.IOException
import java.time.Duration

object BlizzardHttpClientConfig : WithLogger("blizzardClient") {

    fun create(engine: HttpClientEngine, retryConfig: RetryConfig, blizzardAuthClient: BlizzardAuthClient): HttpClient {
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

        return HttpClient(engine) {
            install(HttpRequestRetry) {
                maxRetries = retryConfig.maxAttempts
                retryIf { _, response ->
                    response.status.value >= 500 || response.status == HttpStatusCode.TooManyRequests
                }
                retryOnExceptionIf { _, cause -> cause is IOException }
                delayMillis { retryConfig.delayTime }
                modifyRequest { request ->
                    logger.info("Retry {} for {}", retryCount, request.url.buildString())
                }
            }
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
            defaultRequest {
                header(HttpHeaders.Accept, "*/*")
            }
        }
    }
}

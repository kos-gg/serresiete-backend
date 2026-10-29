package com.kos.clients.config

import com.kos.clients.RetryConfig
import com.kos.common.WithLogger
import io.github.resilience4j.kotlin.ratelimiter.RateLimiterConfig
import io.github.resilience4j.kotlin.ratelimiter.executeSuspendFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.io.IOException
import java.time.Duration

object RiotHttpClientConfig : WithLogger("riotClient") {

    private const val X_RIOT_TOKEN = "X-Riot-Token"

    fun create(engine: HttpClientEngine, retryConfig: RetryConfig, apiKey: String): HttpClient {
        val perSecondRateLimiter = RateLimiter.of(
            "perSecondLimiter",
            RateLimiterConfig {
                this.limitForPeriod(20)
                    .limitRefreshPeriod(Duration.ofSeconds(1))
                    .timeoutDuration(Duration.ofSeconds(1))
                    .build()
            }
        )

        val perTwoMinuteRateLimiter = RateLimiter.of(
            "perTwoMinuteLimiter",
            RateLimiterConfig {
                this.limitForPeriod(100)
                    .limitRefreshPeriod(Duration.ofMinutes(2))
                    .timeoutDuration(Duration.ofMinutes(2))
                    .build()
            }
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
                    logger.info("Retry {} for {} after {}", retryCount, request.url.buildString(), response?.status ?: cause?.message)
                }
            }
            install(createClientPlugin("RiotRateLimit") {
                on(Send) { request ->
                    perSecondRateLimiter.executeSuspendFunction {
                        perTwoMinuteRateLimiter.executeSuspendFunction { proceed(request) }
                    }
                }
            })
            install(HttpTimeout) {
                requestTimeoutMillis = 8_000
            }
            defaultRequest {
                header(HttpHeaders.Accept, "*/*")
                header(X_RIOT_TOKEN, apiKey)
            }
        }
    }
}

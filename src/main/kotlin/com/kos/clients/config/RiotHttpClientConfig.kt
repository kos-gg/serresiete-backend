package com.kos.clients.config

import com.kos.clients.RetryConfig
import com.kos.clients.redactAccessKey
import io.github.resilience4j.kotlin.ratelimiter.RateLimiterConfig
import io.github.resilience4j.kotlin.ratelimiter.executeSuspendFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import org.slf4j.LoggerFactory
import java.time.Duration

private const val X_RIOT_TOKEN = "X-Riot-Token"

private val logger = LoggerFactory.getLogger("riotClient")

fun HttpClientConfig<*>.riotHttpClientConfig(retryConfig: RetryConfig, apiKey: String) {
    val perSecondRateLimiter = RateLimiter.of(
        "riotPerSecondRateLimiter",
        RateLimiterConfig {
            this.limitForPeriod(20)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(1))
                .build()
        }
    )

    val perTwoMinuteRateLimiter = RateLimiter.of(
        "riotPerTwoMinutesRateLimiter",
        RateLimiterConfig {
            this.limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofMinutes(2))
                .timeoutDuration(Duration.ofMinutes(2))
                .build()
        }
    )

    val RiotRateLimitPlugin = createClientPlugin("RiotRateLimit") {
        on(Send) { request ->
            perSecondRateLimiter.executeSuspendFunction {
                perTwoMinuteRateLimiter.executeSuspendFunction { proceed(request) }
            }
        }
    }

    install(HttpRequestRetry) { defaultRetry(retryConfig, logger, ::redactAccessKey) }
    install(RiotRateLimitPlugin)
    install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
    defaultRequest {
        header(X_RIOT_TOKEN, apiKey)
    }
}

package com.kos.clients.config

import com.kos.clients.RetryConfig
import com.kos.clients.redactAccessKey
import io.github.resilience4j.kotlin.ratelimiter.RateLimiterConfig
import io.github.resilience4j.kotlin.ratelimiter.executeSuspendFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import org.slf4j.LoggerFactory
import java.time.Duration

private val logger = LoggerFactory.getLogger("RaiderioClient")

fun HttpClientConfig<*>.raiderIoHttpClientConfig(retryConfig: RetryConfig, apiKey: String) {
    val rateLimiter = RateLimiter.of(
        "raiderIoRateLimiter",
        RateLimiterConfig {
            this.limitForPeriod(1000)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofSeconds(5))
                .build()
        }
    )

    installRetry(retryConfig, logger, ::redactAccessKey)
    install(createClientPlugin("RaiderIoRateLimit") {
        on(Send) { request -> rateLimiter.executeSuspendFunction { proceed(request) } }
    })
    install(HttpTimeout) {
        requestTimeoutMillis = 8_000
    }
    install(createClientPlugin("RaiderIoAccessKey") {
        onRequest { request, _ -> request.url.parameters.append("access_key", apiKey) }
    })
}

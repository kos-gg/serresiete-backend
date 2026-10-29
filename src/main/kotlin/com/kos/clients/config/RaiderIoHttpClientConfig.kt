package com.kos.clients.config

import com.kos.clients.RetryConfig
import com.kos.clients.redactAccessKey
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

object RaiderIoHttpClientConfig : WithLogger("RaiderioClient") {

    fun create(engine: HttpClientEngine, retryConfig: RetryConfig, apiKey: String): HttpClient {
        val rateLimiter = RateLimiter.of(
            "raiderIoRateLimiter",
            RateLimiterConfig {
                this.limitForPeriod(1000)
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .timeoutDuration(Duration.ofSeconds(5))
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
                    logger.info("Retry {} for {}", retryCount, redactAccessKey(request.url.buildString()))
                }
            }
            install(createClientPlugin("RaiderIoRateLimit") {
                on(Send) { request -> rateLimiter.executeSuspendFunction { proceed(request) } }
            })
            install(HttpTimeout) {
                requestTimeoutMillis = 8_000
            }
            install(createClientPlugin("RaiderIoAccessKey") {
                onRequest { request, _ -> request.url.parameters.append("access_key", apiKey) }
            })
            defaultRequest {
                header(HttpHeaders.Accept, "*/*")
            }
        }
    }
}

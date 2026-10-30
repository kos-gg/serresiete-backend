package com.kos.clients.config

import com.kos.clients.RetryConfig
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.utils.*
import io.ktor.http.*
import org.slf4j.Logger
import java.io.IOException

fun HttpClientConfig<*>.installRetry(
    retryConfig: RetryConfig,
    logger: Logger,
    redact: (String) -> String = { it }
) {
    install(HttpRequestRetry) {
        maxRetries = retryConfig.maxAttempts
        retryIf { _, response ->
            response.status.value >= 500 || response.status == HttpStatusCode.TooManyRequests
        }
        retryOnExceptionIf { _, cause -> cause.unwrapCancellationException() is IOException }
        delayMillis { retryConfig.delayTime }
        modifyRequest { request ->
            logger.info(
                "Retry {} for {} after {}",
                retryCount,
                redact(request.url.buildString()),
                response?.status ?: cause?.message?.let(redact)
            )
        }
    }
}

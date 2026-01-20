package com.kos.clients

import arrow.core.Either
import com.kos.common.WithLogger
import kotlinx.coroutines.delay

data class RetryConfig(val maxAttempts: Int, val delayTime: Long)

object Retry : WithLogger("retry") {
    suspend fun <L, R> retryEitherWithFixedDelay(
        retryConfig: RetryConfig,
        functionName: String,
        request: suspend () -> Either<L, R>
    ): Either<L, R> {
        return _retryEitherWithFixedDelay(
            retryConfig.maxAttempts,
            retryConfig.delayTime,
            functionName,
            request
        )
    }

    private suspend fun <L, R> _retryEitherWithFixedDelay(
        retries: Int,
        delayTime: Long,
        functionName: String,
        request: suspend () -> Either<L, R>
    ): Either<L, R> {
        return when (val result = request()) {
            is Either.Right -> result

            is Either.Left -> {
                if (shouldRetry(result.value)) {
                    logger.info("Retry aborted for $functionName due to non-retryable error")
                    return result
                }

                if (retries > 0) {
                    logger.info("Retries left $retries for $functionName")
                    logger.debug("Last error: ${result.value}")
                    delay(delayTime)
                    _retryEitherWithFixedDelay(retries - 1, delayTime, functionName, request)
                } else {
                    logger.debug("Failed retrying for $functionName with ${result.value}")
                    result
                }
            }
        }
    }

    suspend fun <L, R> retryEitherWithExponentialBackoff(
        retryConfig: RetryConfig,
        factor: Double = 2.0,
        maxDelayMillis: Long = Long.MAX_VALUE,
        request: suspend () -> Either<L, R>
    ): Either<L, R> {
        return _retryEitherWithExponentialBackoff(
            retryConfig.maxAttempts,
            retryConfig.delayTime,
            factor,
            maxDelayMillis,
            request
        )
    }

    private suspend fun <L, R> _retryEitherWithExponentialBackoff(
        maxAttempts: Int,
        initialDelayMillis: Long = 100,
        factor: Double = 2.0,
        maxDelayMillis: Long = Long.MAX_VALUE,
        request: suspend () -> Either<L, R>
    ): Either<L, R> {
        return when (val res = request()) {
            is Either.Right -> res
            is Either.Left -> {
                if (maxAttempts > 0) {
                    delay(initialDelayMillis)
                    val nextDelay = (initialDelayMillis * factor).coerceAtMost(maxDelayMillis.toDouble()).toLong()
                    logger.info("Retries left $maxAttempts, next delay: $nextDelay")
                    logger.debug("Last error: ${res.value}")
                    _retryEitherWithExponentialBackoff(
                        maxAttempts - 1,
                        nextDelay,
                        factor,
                        maxDelayMillis,
                        request
                    )
                } else res
            }
        }
    }

    private fun <L> shouldRetry(error: L): Boolean {
        return when (error) {
            is HttpError -> error.status >= 500 || error.status == 429
            else -> false
        }
    }
}
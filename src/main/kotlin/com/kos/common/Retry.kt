package com.kos.common

import arrow.core.Either
import kotlinx.coroutines.delay

data class RetryConfig(val maxAttempts: Int, val delayTime: Long)

object Retry : WithLogger("retry") {
    suspend fun <L, R> retryEitherWithFixedDelay(
        retryConfig: RetryConfig,
        functionName: String,
        block: suspend () -> Either<L, R>
    ): Either<L, R> {
        return _retryEitherWithFixedDelay(retryConfig.maxAttempts, retryConfig.delayTime, functionName, block)
    }

    suspend fun <L, R> retryEitherWithExponentialBackoff(
        retryConfig: RetryConfig,
        factor: Double = 2.0,
        maxDelayMillis: Long = Long.MAX_VALUE,
        block: suspend () -> Either<L, R>
    ): Either<L, R> {
        return _retryEitherWithExponentialBackoff(retryConfig.maxAttempts, retryConfig.delayTime, factor, maxDelayMillis, block)
    }

    private suspend fun <L, R> _retryEitherWithFixedDelay(
        retries: Int,
        delayTime: Long = 1200L,
        functionName: String,
        block: suspend () -> Either<L, R>
    ): Either<L, R> {
        return when (val res = block()) {
            is Either.Right -> res
            is Either.Left ->
                if (retries > 0) {
                    logger.info("Retries left $retries for $functionName")
                    logger.error("Last error: ${res.value}")
                    delay(delayTime)
                    _retryEitherWithFixedDelay(retries - 1, delayTime, functionName, block)
                } else {
                    logger.error("Failed retrying with $res")
                    res
                }
        }
    }

    private suspend fun <L, R> _retryEitherWithExponentialBackoff(
        maxAttempts: Int,
        initialDelayMillis: Long = 100,
        factor: Double = 2.0,
        maxDelayMillis: Long = Long.MAX_VALUE,
        block: suspend () -> Either<L, R>
    ): Either<L, R> {
        return when (val res = block()) {
            is Either.Right -> res
            is Either.Left -> {
                if (maxAttempts > 0) {
                    delay(initialDelayMillis)
                    val nextDelay = (initialDelayMillis * factor).coerceAtMost(maxDelayMillis.toDouble()).toLong()
                    logger.info("Retries left $maxAttempts, next delay: $nextDelay")
                    logger.error("Last error: ${res.value}")
                    _retryEitherWithExponentialBackoff(
                        maxAttempts - 1,
                        nextDelay,
                        factor,
                        maxDelayMillis,
                        block
                    )
                } else res
            }
        }
    }
}
package com.kos.common

import arrow.core.Either
import com.kos.clients.Retry
import com.kos.clients.RetryConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RetryTest {
    private val zeroDelayRetryConfig = RetryConfig(maxAttempts = 3, delayTime = 0L)

    @Test
    fun `retryEitherWithFixedDelay retries the specified number of times on failure`() {
        runBlocking {
            val attempts = mutableListOf<Either<String, String>>()

            val block: suspend () -> Either<String, String> = {
                val result = Either.Left("Failure")
                attempts.add(result)
                result
            }

            val result = Retry.retryEitherWithFixedDelay(zeroDelayRetryConfig, "testFunction", block)

            assertEquals(Either.Left("Failure"), result)
            assertEquals(4, attempts.size)
        }
    }

    @Test
    fun `retryEitherWithFixedDelay returns Right on success before max attempts`() {
        runBlocking {
            val attempts = mutableListOf<Either<String, String>>()

            val block: suspend () -> Either<String, String> = {
                val result = if (attempts.size == 1) Either.Right("Success") else Either.Left("Failure")
                attempts.add(result)
                result
            }

            val result = Retry.retryEitherWithFixedDelay(zeroDelayRetryConfig, "testFunction", block)

            assertEquals(Either.Right("Success"), result)
            assertEquals(2, attempts.size)
        }
    }

    @Test
    fun `retryEitherWithExponentialBackoff retries with exponential delay on failure`() {
        runBlocking {
            val attempts = mutableListOf<Either<String, String>>()

            val request: suspend () -> Either<String, String> = {
                val result = Either.Left("Failure")
                attempts.add(result)
                result
            }

            val result =
                Retry.retryEitherWithExponentialBackoff(zeroDelayRetryConfig, request = request)

            assertEquals(Either.Left("Failure"), result)
            assertEquals(4, attempts.size)
        }
    }

    @Test
    fun `retryEitherWithExponentialBackoff returns Right on success before max attempts`() {
        runBlocking {
            val attempts = mutableListOf<Either<String, String>>()

            val request: suspend () -> Either<String, String> = {
                val result = if (attempts.size == 1) Either.Right("Success") else Either.Left("Failure")
                attempts.add(result)
                result
            }

            val result = Retry.retryEitherWithExponentialBackoff(zeroDelayRetryConfig, request = request)

            assertEquals(Either.Right("Success"), result)
            assertEquals(2, attempts.size)
        }
    }
}
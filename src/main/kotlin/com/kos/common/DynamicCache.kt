package com.kos.common

import arrow.atomic.Atomic
import arrow.atomic.update
import arrow.atomic.value
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DynamicCache<T> : WithLogger("dynamicCache") {

    //TODO: Add size limit. We don't want to fuck up heap because we stored millions of values.
    private val cache: MutableMap<String, T> = mutableMapOf()
    private val inFlight: MutableMap<String, CompletableDeferred<T>> = mutableMapOf()
    private val mutex = Mutex()
    private val hits = Atomic<Int>(0)
    private val miss = Atomic<Int>(0)

    val hitRate: Double
        get() = if (hits.value + miss.value == 0) 0.0 else hits.value.toDouble() / (hits.value + miss.value)

    val numberOfAccess: Int
        get() = hits.value + miss.value

    // Returns the cached value for [id], or calls [fetch] to produce it if absent.
    //
    // The mutex is held only for the fast map-check-and-register step, so fetches
    // for different keys run fully in parallel. For the same key, only the first
    // caller executes [fetch]; all concurrent callers that arrive while the fetch is
    // in progress receive a CompletableDeferred and simply await its result — no
    // duplicate network calls are made.
    //
    // Three paths through this function:
    //
    //   1. CACHE HIT — value already stored → return immediately, increment hits.
    //
    //   2. IN-FLIGHT HIT — another coroutine is already fetching this key → grab its
    //      CompletableDeferred, release the lock, and suspend until it completes.
    //      Counted as a hit because no extra fetch was issued.
    //
    //   3. MISS — no value and no in-flight fetch → register a new CompletableDeferred
    //      under this key, release the lock, call fetch() without holding the lock,
    //      store the result, complete the deferred so any waiters unblock, increment miss.
    //      On failure the deferred is completed exceptionally and removed from inFlight
    //      so future callers can retry.
    //
    // Example — three coroutines requesting the same key concurrently:
    //
    //   coroutine A  →  MISS   : creates deferred, calls fetch() (network call)
    //   coroutine B  →  IN-FLIGHT HIT : finds A's deferred, awaits it
    //   coroutine C  →  IN-FLIGHT HIT : finds A's deferred, awaits it
    //   ... fetch() completes ...
    //   A stores result, completes deferred  →  B and C both unblock with the same value
    //   Result: 1 network call, 3 coroutines satisfied.
    //
    // Example — two coroutines requesting different keys concurrently:
    //
    //   coroutine A  →  MISS for "key-1" : registers deferred-1, releases lock, calls fetch("key-1")
    //   coroutine B  →  MISS for "key-2" : registers deferred-2, releases lock, calls fetch("key-2")
    //   fetch("key-1") and fetch("key-2") run in parallel — the lock is not held during either call.
    suspend fun get(id: String, fetch: suspend () -> T): T {
        val (deferred, shouldFetch) = mutex.withLock {
            cache[id]?.let { cached ->
                logger.debug("hit in cache for $id")
                hits.update { it + 1 }
                return cached                          // path 1: cache hit
            }
            inFlight[id]?.let { existing ->
                logger.debug("awaiting in-flight fetch for $id")
                hits.update { it + 1 }
                return@withLock Pair(existing, false)  // path 2: in-flight hit
            }
            logger.debug("no hit in cache for $id")
            miss.update { it + 1 }
            val d = CompletableDeferred<T>()
            inFlight[id] = d
            Pair(d, true)                              // path 3: miss, this coroutine fetches
        }

        return if (shouldFetch) {
            try {
                val result = fetch()
                mutex.withLock {
                    cache[id] = result
                    inFlight.remove(id)
                }
                deferred.complete(result)
                result
            } catch (e: Throwable) {
                mutex.withLock { inFlight.remove(id) }
                deferred.completeExceptionally(e)      // waiters receive the exception; they can retry
                throw e
            }
        } else {
            deferred.await()
        }
    }

}

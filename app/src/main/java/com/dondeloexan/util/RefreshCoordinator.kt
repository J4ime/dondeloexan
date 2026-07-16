package com.dondeloexan.util

import java.net.SocketTimeoutException
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class RefreshCoordinator {

    private val semaphore = Semaphore(3)
    private val consecutiveFailures = AtomicInteger(0)
    private val batchCancelled = AtomicBoolean(false)

    suspend fun <T> execute(context: CoroutineContext, tmdbId: Int, block: suspend () -> T): T {
        return withContext(context) {
            if (batchCancelled.get()) throw BatchCancelledException()

            semaphore.acquire()
            try {
                val result = block()
                consecutiveFailures.set(0)
                result
            } catch (e: SocketTimeoutException) {
                val fails = consecutiveFailures.incrementAndGet()
                if (fails >= 3) {
                    batchCancelled.set(true)
                }
                throw e
            } finally {
                semaphore.release()
            }
        }
    }

    fun resetBatch() {
        consecutiveFailures.set(0)
        batchCancelled.set(false)
    }
}

class BatchCancelledException : Exception("Circuito abierto: 3 timeouts consecutivos")

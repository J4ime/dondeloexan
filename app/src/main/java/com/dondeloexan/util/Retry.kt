package com.dondeloexan.util

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

suspend fun <T> retryWithBackoff(
    maxRetries: Int = 2,
    initialDelayMs: Long = 1_000,
    maxDelayMs: Long = 2_000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    for (attempt in 0..maxRetries) {
        try {
            return block()
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            lastException = e
            if (attempt == maxRetries) throw e
        } catch (e: java.net.SocketTimeoutException) {
            lastException = e
            if (attempt == maxRetries) throw e
        } catch (e: java.io.IOException) {
            if (e.message?.contains("timeout", ignoreCase = true) == true) {
                lastException = e
                if (attempt == maxRetries) throw e
            } else throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        }
        val delayMs = min(initialDelayMs * factor.pow(attempt), maxDelayMs.toDouble()).toLong()
        delay(delayMs)
    }
    throw lastException ?: Exception("Retry failed")
}

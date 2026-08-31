package com.dondeloexan.util

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class HttpTracingInterceptor(private val tag: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val start = System.nanoTime()
        Log.d(tag, ">>> ${request.method} ${request.url}")
        request.headers.names()
            .sorted()
            .forEach { name ->
                val value = request.header(name).orEmpty()
                if (name.equals("Authorization", ignoreCase = true)) {
                    Log.d(tag, "    $name: Bearer <len=${value.length}>")
                } else {
                    Log.d(tag, "    $name: $value")
                }
            }
        return try {
            val response = chain.proceed(request)
            val ms = (System.nanoTime() - start) / 1_000_000
            val bytes = response.body?.contentLength() ?: -1L
            Log.d(tag, "<<< ${response.code} ${request.url} (${ms}ms, ${bytes} bytes, isSuccessful=${response.isSuccessful})")
            response
        } catch (e: IOException) {
            val ms = (System.nanoTime() - start) / 1_000_000
            Log.e(tag, "!!! ${request.method} ${request.url} ERROR ${e.javaClass.simpleName}: ${e.message} (${ms}ms)")
            throw e
        }
    }
}
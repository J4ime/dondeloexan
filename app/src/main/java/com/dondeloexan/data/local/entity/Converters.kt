package com.dondeloexan.data.local.entity

import androidx.room.TypeConverter
import com.dondeloexan.domain.model.AvailabilityType
import com.dondeloexan.domain.model.StreamingAvailability
import com.dondeloexan.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject

class Converters {

    @TypeConverter
    fun fromWatchStatus(status: WatchStatus): String = status.name

    @TypeConverter
    fun toWatchStatus(value: String): WatchStatus = WatchStatus.valueOf(value)
}

fun String?.toStreamingPlatforms(): List<StreamingAvailability> {
    if (this == null) {
        AppLogger.d("Converters", "toStreamingPlatforms: input=null")
        return emptyList()
    }
    return try {
        val jsonArray = JSONArray(this)
        val result = (0 until jsonArray.length()).mapNotNull { i ->
            val obj = jsonArray.optJSONObject(i) ?: return@mapNotNull null
            StreamingAvailability(
                platformName = obj.optString("platformName", null)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null,
                platformId = obj.optString("platformId", null),
                logoUrl = obj.optString("logoUrl", null)?.takeIf { it.isNotEmpty() },
                availabilityType = try {
                    AvailabilityType.valueOf(obj.optString("availabilityType", "SUBSCRIPTION"))
                } catch (e: Exception) {
                    AppLogger.e("Converters", "Unknown availabilityType", e)
                    AvailabilityType.SUBSCRIPTION
                }
            )
        }
        AppLogger.d("Converters", "toStreamingPlatforms: parsed ${result.size} platforms, len=${this.length}, preview=${this.take(120)}")
        result
    } catch (e: Exception) {
        AppLogger.e("Converters", "toStreamingPlatforms error, trying fallback: ${this?.take(200)}", e)
        tryFallbackParse(this) ?: emptyList()
    }
}

private fun tryFallbackParse(badJson: String?): List<StreamingAvailability>? {
    val input = badJson ?: return null
    return try {
        val inner = input.trimStart('[').trimEnd(']')
        if (!inner.startsWith("{") || !inner.endsWith("}")) return null

        val objects = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in inner.indices) {
            when (inner[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        objects.add(inner.substring(start, i + 1))
                        start = i + 1
                    }
                }
            }
        }
        if (objects.isEmpty()) return null

        val fixed = objects.mapNotNull { obj ->
            val content = obj.removeSurrounding("{", "}")
            val pairs = mutableMapOf<String, String>()
            var remaining = content
            for (key in listOf("platformName", "platformId", "logoUrl", "availabilityType")) {
                val prefix = "\"$key\":"
                val idx = remaining.indexOf(prefix)
                if (idx < 0) return@mapNotNull null
                val afterPrefix = remaining.substring(idx + prefix.length)
                val endIdx = afterPrefix.indexOfFirst { it == ',' || it == '}' }.let { if (it < 0) afterPrefix.length else it }
                val value = afterPrefix.substring(0, endIdx)
                pairs[key] = value
                remaining = afterPrefix.substring(endIdx)
            }
            if (pairs.size < 4) return@mapNotNull null
            StreamingAvailability(
                platformName = pairs["platformName"]?.trim()?.trimEnd(',')?.trim('"') ?: return@mapNotNull null,
                platformId = pairs["platformId"]?.trim()?.trimEnd(',')?.trim('"'),
                logoUrl = pairs["logoUrl"]?.trim()?.trimEnd(',')?.trim('"')?.takeIf { it.isNotEmpty() },
                availabilityType = try {
                    AvailabilityType.valueOf(pairs["availabilityType"]?.trim()?.trimEnd(',')?.trim('"') ?: "SUBSCRIPTION")
                } catch (_: Exception) { AvailabilityType.SUBSCRIPTION }
            )
        }

        if (fixed.isEmpty()) return null
        AppLogger.d("Converters", "tryFallbackParse: recovered ${fixed.size} platforms")
        fixed
    } catch (e: Exception) {
        AppLogger.e("Converters", "tryFallbackParse failed", e)
        null
    }
}

fun List<StreamingAvailability>.toPlatformsString(): String? {
    return try {
        val jsonArray = JSONArray()
        this@toPlatformsString.forEach { platform ->
            val obj = JSONObject()
            obj.put("platformName", platform.platformName)
            obj.put("platformId", platform.platformId ?: "")
            obj.put("logoUrl", platform.logoUrl ?: "")
            obj.put("availabilityType", platform.availabilityType.name)
            jsonArray.put(obj)
        }
        val result = jsonArray.toString()
        AppLogger.d("Converters", "toPlatformsString: ${this@toPlatformsString.size} platforms, len=${result.length}, preview=${result.take(120)}")
        result
    } catch (e: Exception) {
        AppLogger.e("Converters", "toPlatformsString error", e)
        null
    }
}

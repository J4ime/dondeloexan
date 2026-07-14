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
        AppLogger.e("Converters", "toStreamingPlatforms error: ${this?.take(200)}", e)
        emptyList()
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

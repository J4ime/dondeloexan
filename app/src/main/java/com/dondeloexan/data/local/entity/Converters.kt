package com.dondeloexan.data.local.entity

import androidx.room.TypeConverter
import com.dondeloexan.domain.model.AvailabilityType
import com.dondeloexan.domain.model.StreamingAvailability
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class Converters {

    @TypeConverter
    fun fromWatchStatus(status: WatchStatus): String = status.name

    @TypeConverter
    fun toWatchStatus(value: String): WatchStatus = WatchStatus.valueOf(value)
}

fun String?.toStreamingPlatforms(): List<StreamingAvailability> {
    if (this == null) return emptyList()
    return try {
        val json = Json.parseToJsonElement(this) as? JsonArray ?: return emptyList()
        json.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            StreamingAvailability(
                platformName = (obj["platformName"] as? JsonPrimitive)?.content ?: return@mapNotNull null,
                platformId = (obj["platformId"] as? JsonPrimitive)?.content,
                logoUrl = (obj["logoUrl"] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() },
                availabilityType = (obj["availabilityType"] as? JsonPrimitive)?.content?.let {
                    try { AvailabilityType.valueOf(it) } catch (_: Exception) { null }
                } ?: AvailabilityType.SUBSCRIPTION
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun List<StreamingAvailability>.toPlatformsString(): String? {
    if (isEmpty()) return null
    return try {
        buildString {
            append("[")
            this@toPlatformsString.forEachIndexed { index, platform ->
                if (index > 0) append(",")
                append("""{"platformName":""")
                append(platform.platformName.replace("\"", "\\\""))
                append(""","platformId":""")
                append((platform.platformId ?: "").replace("\"", "\\\""))
                append(""","logoUrl":""")
                append((platform.logoUrl ?: "").replace("\"", "\\\""))
                append(""","availabilityType":""")
                append(platform.availabilityType.name)
                append("""}""")
            }
            append("]")
        }
    } catch (_: Exception) { null }
}

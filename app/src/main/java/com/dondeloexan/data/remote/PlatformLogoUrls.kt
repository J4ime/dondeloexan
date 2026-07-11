package com.dondeloexan.data.remote

object PlatformLogoUrls {

    private val logoPaths = mapOf(
        "Netflix" to "/t2yyOv40HZeVlLjYsCsPHnWLk4W.jpg",
        "Prime Video" to "/emthp39XA2YScoYL1p0CdbjOl9d.jpg",
        "Disney+" to "/dgPueyEdOwpQ10fjuhL2WYFQwQs.jpg",
        "HBO Max" to "/aS2zvJWn9mwiCOeaaCkIh4wleZS.jpg",
        "Apple TV+" to "/4KAy34EHvRM25Ih8wb82AuGU0zJ.jpg",
        "Movistar+" to "/e8nMpNq7ZFFP3Hqp6EtV7hP5FmN.jpg",
        "Paramount+" to "/h5O3m43xNdBOb1bLgRizwGnfi7U.jpg",
        "SkyShowtime" to "/jXRL3vWibhRcBXKjH2mYDdKj0zA.jpg",
        "Filmin" to "/wEFvMF4NvsR46xGCjggNhs7hybm.jpg"
    )

    private const val BASE_URL = "https://image.tmdb.org/t/p/w92"

    fun urlFor(name: String): String? {
        val path = logoPaths[name] ?: return null
        return "$BASE_URL$path"
    }
}

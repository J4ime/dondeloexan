package com.dondeloexan.util

import java.time.LocalDate
import java.time.Period

object PersonFlagUtil {
    private val countryToCode = mapOf(
        "usa" to "US", "united states" to "US",
        "uk" to "GB", "united kingdom" to "GB", "england" to "GB",
        "spain" to "ES", "espa\u00f1a" to "ES",
        "france" to "FR", "mexico" to "MX", "m\u00e9xico" to "MX",
        "canada" to "CA", "argentina" to "AR",
        "australia" to "AU", "germany" to "DE", "alemania" to "DE",
        "italy" to "IT", "italia" to "IT",
        "japan" to "JP", "china" to "CN",
        "india" to "IN", "brazil" to "BR", "brasil" to "BR",
        "sweden" to "SE", "norway" to "NO", "denmark" to "DK",
        "netherlands" to "NL", "belgium" to "BE",
        "switzerland" to "CH", "austria" to "AT",
        "ireland" to "IE", "russia" to "RU",
        "new zealand" to "NZ", "portugal" to "PT",
        "colombia" to "CO", "chile" to "CL", "peru" to "PE",
        "puerto rico" to "PR", "cuba" to "CU",
        "hungary" to "HU", "poland" to "PL",
        "turkey" to "TR", "t\u00fcrkiye" to "TR",
        "south korea" to "KR", "korea" to "KR",
        "taiwan" to "TW", "hong kong" to "HK",
        "venezuela" to "VE", "uruguay" to "UY",
        "costa rica" to "CR", "dominican republic" to "DO",
        "finland" to "FI", "finlandia" to "FI",
        "romania" to "RO", "rumania" to "RO",
        "greece" to "GR", "grecia" to "GR",
        "czech republic" to "CZ", "chequia" to "CZ",
        "nigeria" to "NG", "south africa" to "ZA",
        "egypt" to "EG", "egypto" to "EG"
    )

    fun countryFlag(placeOfBirth: String?): String {
        val country = placeOfBirth?.substringAfterLast(",")?.trim()?.lowercase() ?: return ""
        val code = countryToCode[country] ?: return ""
        return buildString {
            code.forEach { append(Character.toChars(0x1F1E6 + (it - 'A'))) }
        }
    }

    fun age(birthday: String?, deathday: String?): Int? {
        val birthStr = birthday ?: return null
        val birthDate = try { LocalDate.parse(birthStr) } catch (e: Exception) { return null }
        val endDate = try {
            deathday?.let { LocalDate.parse(it) } ?: LocalDate.now()
        } catch (e: Exception) { LocalDate.now() }
        return Period.between(birthDate, endDate).years
    }
}

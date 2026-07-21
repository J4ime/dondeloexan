package com.dondeloexan.data.remote.mapper

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CertificationDisplay(
    val label: String,
    val icon: ImageVector,
    val color: Color
)

fun String?.toCertificationDisplay(): CertificationDisplay? = when (this) {
    "G", "TV-Y", "TV-G" -> CertificationDisplay("Todos los públicos", Icons.Filled.SentimentSatisfied, Color(0xFF4CAF50))
    "PG", "TV-PG" -> CertificationDisplay("Supervisión parental", Icons.Filled.Person, Color(0xFFFFC107))
    "TV-Y7" -> CertificationDisplay("Mayores de 7", Icons.Filled.Person, Color(0xFFFF9800))
    "PG-13" -> CertificationDisplay("Mayores de 13", Icons.Filled.Person, Color(0xFFFF5722))
    "R", "TV-14" -> CertificationDisplay("Mayores de 16", Icons.Filled.Shield, Color(0xFFF44336))
    "NC-17", "TV-MA", "18" -> CertificationDisplay("Mayores de 18", Icons.Filled.GppBad, Color(0xFFB71C1C))
    "Not Rated", "NR", "Unrated", "Approved" -> CertificationDisplay("Sin clasificar", Icons.Filled.HelpOutline, Color(0xFF9E9E9E))
    else -> null
}

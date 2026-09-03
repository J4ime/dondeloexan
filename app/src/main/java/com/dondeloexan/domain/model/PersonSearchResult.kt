package com.dondeloexan.domain.model

data class PersonSearchResult(
    val id: Int,
    val name: String,
    val profilePath: String? = null,
    val knownForDepartment: String? = null
)

data class CompanySearchResult(
    val id: Int,
    val name: String,
    val logoPath: String? = null
)

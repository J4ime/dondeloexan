package com.dondeloexan.domain.model

sealed interface DataResult<out T> {
    data object Loading : DataResult<Nothing>
    data class Success<T>(val data: T) : DataResult<T>
    data class Error(val exception: Throwable) : DataResult<Nothing>
}

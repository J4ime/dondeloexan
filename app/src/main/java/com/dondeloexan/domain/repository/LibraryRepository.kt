package com.dondeloexan.domain.repository

import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.PlatformReleaseDate
import kotlinx.coroutines.flow.Flow

enum class LibraryAction { FAVORITE, ADD, WATCHED, BLACKLIST }

data class LibraryMutationResult(
    val action: LibraryAction,
    val toggledOn: Boolean,
    val isMovie: Boolean,
    val title: String
)

/**
 * Repositorio del estado de la biblioteca local del usuario tal y como se
 * consulta/muta desde el catálogo "Descubrir": favoritos, pendientes, vistos,
 * progreso de series, lista negra y plataformas activas.
 */
interface LibraryRepository {
    val likedIds: Flow<Set<String>>
    val watchedIds: Flow<Set<String>>
    val libraryIds: Flow<Set<String>>
    val blacklistedIds: Flow<Set<String>>
    val activePlatforms: Flow<Set<String>>

    suspend fun discoverExcludedIds(): Set<String>

    suspend fun toggleFavorite(preview: ContentPreview): LibraryMutationResult
    suspend fun toggleInLibrary(preview: ContentPreview): LibraryMutationResult
    suspend fun toggleWatched(preview: ContentPreview): LibraryMutationResult
    suspend fun toggleBlacklist(preview: ContentPreview): LibraryMutationResult

    suspend fun faReleases(contentId: String): List<PlatformReleaseDate>
}

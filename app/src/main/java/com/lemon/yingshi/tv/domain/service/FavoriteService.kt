package com.lemon.yingshi.tv.domain.service

import com.lemon.yingshi.tv.data.local.database.dao.FavoriteDao
import com.lemon.yingshi.tv.data.local.database.entity.FavoriteEntity
import com.lemon.yingshi.tv.domain.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteService @Inject constructor(
    private val favoriteDao: FavoriteDao
) {

    fun getAllFavorites(): Flow<List<FavoriteItem>> {
        return favoriteDao.getAllFavorites().map { entities ->
            entities.map { it.toFavoriteItem() }
        }
    }

    suspend fun isFavorite(mediaId: String): Boolean {
        return favoriteDao.isFavorite(mediaId)
    }

    suspend fun addFavorite(
        mediaId: String,
        title: String,
        posterUrl: String? = null,
        backdropUrl: String? = null,
        overview: String? = null,
        year: String? = null,
        genres: List<String> = emptyList(),
        mediaType: MediaType = MediaType.OTHER,
        rating: Float? = null
    ) {
        favoriteDao.insertFavorite(
            FavoriteEntity(
                mediaId = mediaId,
                title = title,
                posterUrl = posterUrl,
                backdropUrl = backdropUrl,
                overview = overview,
                year = year,
                genres = genres.takeIf { it.isNotEmpty() }?.joinToString(","),
                mediaType = mediaType,
                rating = rating,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeFavorite(mediaId: String) {
        favoriteDao.deleteFavorite(mediaId)
    }

    suspend fun toggleFavorite(
        mediaId: String,
        title: String,
        posterUrl: String? = null,
        backdropUrl: String? = null,
        overview: String? = null,
        year: String? = null,
        genres: List<String> = emptyList(),
        mediaType: MediaType = MediaType.OTHER,
        rating: Float? = null
    ): Boolean {
        return if (favoriteDao.isFavorite(mediaId)) {
            favoriteDao.deleteFavorite(mediaId)
            false
        } else {
            addFavorite(
                mediaId = mediaId,
                title = title,
                posterUrl = posterUrl,
                backdropUrl = backdropUrl,
                overview = overview,
                year = year,
                genres = genres,
                mediaType = mediaType,
                rating = rating
            )
            true
        }
    }

    suspend fun clearAllFavorites() {
        favoriteDao.clearAllFavorites()
    }

    private fun FavoriteEntity.toFavoriteItem(): FavoriteItem {
        return FavoriteItem(
            mediaId = mediaId,
            title = title,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            overview = overview,
            year = year,
            genres = genres?.split(",")?.filter { it.isNotBlank() }.orEmpty(),
            mediaType = mediaType,
            rating = rating,
            addedAt = addedAt
        )
    }
}

data class FavoriteItem(
    val mediaId: String,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val year: String?,
    val genres: List<String>,
    val mediaType: MediaType,
    val rating: Float?,
    val addedAt: Long
) {
    fun coverImageUrl(): String? = backdropUrl ?: posterUrl
}

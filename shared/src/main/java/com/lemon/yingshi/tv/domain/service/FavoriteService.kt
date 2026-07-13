package com.lemon.yingshi.tv.domain.service

import com.lemon.yingshi.tv.data.local.database.dao.FavoriteDao
import com.lemon.yingshi.tv.data.local.database.entity.FavoriteEntity
import com.lemon.yingshi.tv.data.preferences.MacCmsPreferences
import com.lemon.yingshi.tv.domain.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteService @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val macCmsPreferences: MacCmsPreferences
) {

    fun getAllFavorites(): Flow<List<FavoriteItem>> {
        return macCmsPreferences.serverUrl.flatMapLatest { serverUrl ->
            favoriteDao.getFavoritesByServerUrl(serverUrl).map { entities ->
                entities.map { it.toFavoriteItem() }
            }
        }
    }

    suspend fun isFavorite(mediaId: String): Boolean {
        return favoriteDao.isFavorite(mediaId, currentServerUrl())
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
                serverUrl = currentServerUrl(),
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

    suspend fun removeFavorite(mediaId: String, serverUrl: String? = null) {
        favoriteDao.deleteFavorite(mediaId, serverUrl ?: currentServerUrl())
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
        val serverUrl = currentServerUrl()
        return if (favoriteDao.isFavorite(mediaId, serverUrl)) {
            favoriteDao.deleteFavorite(mediaId, serverUrl)
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
        favoriteDao.clearFavoritesByServerUrl(currentServerUrl())
    }

    private suspend fun currentServerUrl(): String =
        macCmsPreferences.serverUrl.first()

    private fun FavoriteEntity.toFavoriteItem(): FavoriteItem {
        return FavoriteItem(
            mediaId = mediaId,
            serverUrl = serverUrl,
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
    val serverUrl: String,
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

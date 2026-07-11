package com.lemon.yingshi.tv.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.domain.service.FavoriteItem
import com.lemon.yingshi.tv.domain.service.FavoriteService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoriteService: FavoriteService
) : ViewModel() {

    val favorites = favoriteService.getAllFavorites()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun removeFavorite(item: FavoriteItem) {
        viewModelScope.launch {
            favoriteService.removeFavorite(item.mediaId)
        }
    }

    fun clearAllFavorites() {
        viewModelScope.launch {
            favoriteService.clearAllFavorites()
        }
    }
}

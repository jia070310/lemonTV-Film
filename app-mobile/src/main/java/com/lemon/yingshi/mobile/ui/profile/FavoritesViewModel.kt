package com.lemon.yingshi.mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemon.yingshi.tv.domain.service.FavoriteItem
import com.lemon.yingshi.tv.domain.service.FavoriteService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoriteService: FavoriteService
) : ViewModel() {

    val favorites = favoriteService.getAllFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeFavorite(item: FavoriteItem) {
        viewModelScope.launch {
            favoriteService.removeFavorite(item.mediaId, item.serverUrl)
        }
    }

    fun clearAllFavorites() {
        viewModelScope.launch {
            favoriteService.clearAllFavorites()
        }
    }
}

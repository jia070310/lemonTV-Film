package com.lemon.yingshi.mobile.ui.profile



import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import com.lemon.yingshi.tv.data.remote.model.MacCmsIds

import com.lemon.yingshi.tv.data.repository.MacCmsRepository

import com.lemon.yingshi.tv.domain.service.PlaybackInfo

import com.lemon.yingshi.tv.domain.service.WatchHistoryItem

import com.lemon.yingshi.tv.domain.service.WatchHistoryService

import dagger.hilt.android.lifecycle.HiltViewModel

import java.io.File

import javax.inject.Inject

import kotlinx.coroutines.flow.SharingStarted

import kotlinx.coroutines.flow.flatMapLatest

import kotlinx.coroutines.flow.flow

import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch



@HiltViewModel

class WatchHistoryViewModel @Inject constructor(

    private val watchHistoryService: WatchHistoryService,

    private val macCmsRepository: MacCmsRepository

) : ViewModel() {



    val historyItems = watchHistoryService.getRecentWatchHistory(100)

        .flatMapLatest { items ->

            flow { emit(enrichPosterUrls(items)) }

        }

        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())



    suspend fun getPlaybackInfo(item: WatchHistoryItem): PlaybackInfo? {

        return watchHistoryService.getPlaybackInfo(item.mediaId, item.episodeId)

    }



    fun clearAllHistory() {

        viewModelScope.launch {

            watchHistoryService.clearAllWatchHistory()

        }

    }



    private suspend fun enrichPosterUrls(items: List<WatchHistoryItem>): List<WatchHistoryItem> {

        return items.map { item ->

            if (hasCover(item)) return@map item

            val vodId = MacCmsIds.decode(item.mediaId) ?: return@map item

            val poster = macCmsRepository.getCachedVodDetail(vodId)?.vodPic

                ?: macCmsRepository.fetchVodDetail(vodId)?.vodPic

            if (poster.isNullOrBlank()) return@map item

            watchHistoryService.updatePosterUrlIfMissing(item.mediaId, item.episodeId, poster)

            item.copy(posterUrl = poster, backdropUrl = poster)

        }

    }



    private fun hasCover(item: WatchHistoryItem): Boolean {

        item.localCoverPath?.let { path ->

            if (File(path).exists()) return true

        }

        return !item.posterUrl.isNullOrBlank() || !item.backdropUrl.isNullOrBlank()

    }

}


package com.lomen.tv

import android.content.Context
import com.lomen.tv.data.preferences.MediaClassificationPreferences
import com.lomen.tv.data.preferences.MediaClassificationStrategyHolder
import com.lomen.tv.domain.service.AppUpdateService
import com.lomen.tv.domain.service.GuangyaTokenAutoRefresh
import com.lomen.tv.domain.service.PlaybackStatsService
import com.lomen.tv.domain.service.TmdbMetadataSyncManager
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class LomenTVApplication : android.app.Application() {

    @Inject
    @ApplicationContext
    lateinit var appContext: Context
    
    @Inject
    lateinit var appUpdateService: AppUpdateService
    
    @Inject
    lateinit var playbackStatsService: PlaybackStatsService

    @Inject
    lateinit var mediaClassificationPreferences: MediaClassificationPreferences

    @Inject
    lateinit var tmdbMetadataSyncManager: TmdbMetadataSyncManager

    @Inject
    lateinit var guangyaTokenAutoRefresh: GuangyaTokenAutoRefresh

    override fun onCreate() {
        super.onCreate()
        instance = this
        guangyaTokenAutoRefresh.start()
        runBlocking {
            val strategy = mediaClassificationPreferences.classificationStrategy.first()
            MediaClassificationStrategyHolder.update(strategy)
        }
        tmdbMetadataSyncManager.start()
    }

    companion object {
        lateinit var instance: LomenTVApplication
            private set
            
        fun getAppUpdateService(context: Context): AppUpdateService {
            return instance.appUpdateService
        }
        
        fun getPlaybackStatsService(context: Context): PlaybackStatsService {
            return instance.playbackStatsService
        }
    }
}

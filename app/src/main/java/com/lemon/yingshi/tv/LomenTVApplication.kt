package com.lemon.yingshi.tv

import com.lemon.yingshi.tv.domain.service.AppUpdateService
import com.lemon.yingshi.tv.domain.service.PlaybackStatsService
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
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

    override fun onCreate() {
        super.onCreate()
        instance = this
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

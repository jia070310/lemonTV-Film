package com.lemon.yingshi.tv

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lemon.yingshi.tv.domain.service.AppUpdateService
import com.lemon.yingshi.tv.domain.service.DeviceStorageProfile
import com.lemon.yingshi.tv.domain.service.MediaStorageHelper
import com.lemon.yingshi.tv.domain.service.PlaybackStatsService
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
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
    lateinit var mediaStorageHelper: MediaStorageHelper

    override fun onCreate() {
        super.onCreate()
        instance = this
        mediaStorageHelper.onApplicationColdStart()
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve(DeviceStorageProfile.COIL_IMAGE_CACHE_DIR))
                        .maxSizeBytes(DeviceStorageProfile.COIL_DISK_MAX_BYTES)
                        .build()
                }
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.10)
                        .build()
                }
                .build()
        )
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

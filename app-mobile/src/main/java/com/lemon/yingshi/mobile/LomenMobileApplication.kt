package com.lemon.yingshi.mobile

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lemon.yingshi.tv.domain.service.DeviceStorageProfile
import com.lemon.yingshi.tv.domain.service.MediaStorageHelper
import com.lemon.yingshi.tv.domain.service.OfflineDownloadManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LomenMobileApplication : Application() {

    @Inject lateinit var offlineDownloadManager: OfflineDownloadManager
    @Inject lateinit var mediaStorageHelper: MediaStorageHelper

    override fun onCreate() {
        super.onCreate()
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
                        .maxSizePercent(0.15)
                        .build()
                }
                .build()
        )
        offlineDownloadManager.resumeInterruptedDownloads()
    }
}

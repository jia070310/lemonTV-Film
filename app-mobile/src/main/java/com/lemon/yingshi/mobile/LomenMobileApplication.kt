package com.lemon.yingshi.mobile

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lemon.yingshi.mobile.util.MediaStorageHelper
import com.lemon.yingshi.tv.domain.service.OfflineDownloadManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LomenMobileApplication : Application() {

    @Inject lateinit var offlineDownloadManager: OfflineDownloadManager

    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve(MediaStorageHelper.COIL_IMAGE_CACHE_DIR))
                        .maxSizePercent(0.05)
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

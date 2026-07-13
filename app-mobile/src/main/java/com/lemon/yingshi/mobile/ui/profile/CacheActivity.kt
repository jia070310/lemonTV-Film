package com.lemon.yingshi.mobile.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityCacheBinding
import com.lemon.yingshi.mobile.ui.settings.SettingsDialogs
import com.lemon.yingshi.mobile.util.MediaStorageHelper
import com.lemon.yingshi.mobile.util.StorageFormatter
import com.lemon.yingshi.mobile.util.setBackNavigation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CacheActivity : AppCompatActivity() {

    @Inject lateinit var mediaStorageHelper: MediaStorageHelper

    private lateinit var binding: ActivityCacheBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCacheBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setBackNavigation { finish() }
        refreshSizes()

        binding.clearPlaybackCache.setOnClickListener {
            mediaStorageHelper.clearPlaybackCache()
            toastCleared()
            refreshSizes()
        }
        binding.clearCoverCache.setOnClickListener {
            mediaStorageHelper.clearCoverCache()
            toastCleared()
            refreshSizes()
        }
        binding.clearAllCache.setOnClickListener {
            SettingsDialogs.showConfirmDialog(
                context = this,
                message = getString(R.string.cache_clear_all_confirm)
            ) {
                mediaStorageHelper.clearAllCaches()
                toastCleared()
                refreshSizes()
            }
        }
    }

    private fun refreshSizes() {
        val playback = mediaStorageHelper.getPlaybackCacheSizeBytes()
        val cover = mediaStorageHelper.getCoverCacheSizeBytes()
        val total = playback + cover
        binding.playbackCacheSize.text = StorageFormatter.format(playback)
        binding.coverCacheSize.text = StorageFormatter.format(cover)
        binding.totalCacheSize.text = getString(R.string.cache_total) + ": " + StorageFormatter.format(total)
    }

    private fun toastCleared() {
        Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, CacheActivity::class.java)
    }
}

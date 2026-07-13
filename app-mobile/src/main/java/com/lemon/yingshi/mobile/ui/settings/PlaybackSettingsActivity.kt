package com.lemon.yingshi.mobile.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityPlaybackSettingsBinding
import com.lemon.yingshi.mobile.databinding.ItemProfileMenuBinding
import com.lemon.yingshi.tv.domain.service.WatchHistoryService
import com.lemon.yingshi.mobile.util.setBackNavigation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlaybackSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaybackSettingsBinding
    private val playerSettingsViewModel: PlayerSettingsViewModel by viewModels()

    @Inject lateinit var watchHistoryService: WatchHistoryService

    private val seekOptions = listOf(15, 20, 25, 30, 35)
    private var currentSeekSeconds = 15

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaybackSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setBackNavigation { finish() }
        setupSeekDurationMenu()
        setupRememberSwitch()
        setupMenu(
            binding.menuClearHistory,
            R.drawable.ic_settings_clear,
            getString(R.string.settings_clear_history)
        ) {
            SettingsDialogs.showConfirmDialog(
                context = this,
                message = getString(R.string.settings_clear_history_confirm)
            ) {
                lifecycleScope.launch {
                    watchHistoryService.clearAllWatchHistory()
                    Toast.makeText(this@PlaybackSettingsActivity, R.string.settings_cleared, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSeekDurationMenu() {
        binding.menuSeekDuration.menuTitle.text = getString(R.string.settings_seek_duration)
        binding.menuSeekDuration.menuSubtitle.visibility = View.GONE
        binding.menuSeekDuration.menuIcon.setImageResource(R.drawable.ic_settings_seek)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerSettingsViewModel.seekDurationSeconds.collect { seconds ->
                    currentSeekSeconds = seconds
                }
            }
        }

        binding.menuSeekDuration.root.setOnClickListener {
            SettingsDialogs.showSeekDurationMenu(
                context = this,
                options = seekOptions,
                currentSeconds = currentSeekSeconds
            ) { selected ->
                playerSettingsViewModel.setSeekDurationSeconds(selected)
            }
        }
    }

    private fun setupRememberSwitch() {
        val switchBinding = binding.menuRememberPlayback
        switchBinding.menuIcon.setImageResource(R.drawable.ic_settings_remember)
        switchBinding.menuTitle.text = getString(R.string.settings_remember_playback)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerSettingsViewModel.rememberPlaybackPosition.collect { enabled ->
                    if (switchBinding.menuSwitch.isChecked != enabled) {
                        switchBinding.menuSwitch.isChecked = enabled
                    }
                }
            }
        }
        switchBinding.menuSwitch.setOnCheckedChangeListener { _, checked ->
            playerSettingsViewModel.setRememberPlaybackPosition(checked)
        }
    }

    private fun setupMenu(
        menuBinding: ItemProfileMenuBinding,
        iconRes: Int,
        title: String,
        onClick: () -> Unit
    ) {
        menuBinding.menuIcon.setImageResource(iconRes)
        menuBinding.menuTitle.text = title
        menuBinding.menuSubtitle.visibility = View.GONE
        menuBinding.root.setOnClickListener { onClick() }
    }
}

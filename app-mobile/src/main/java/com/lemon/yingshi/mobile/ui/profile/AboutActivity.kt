package com.lemon.yingshi.mobile.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityAboutBinding
import com.lemon.yingshi.mobile.databinding.ItemAboutActionCardBinding
import com.lemon.yingshi.mobile.ui.UpdateDownloadDialog
import com.lemon.yingshi.mobile.ui.UpdateInstallCoordinator
import com.lemon.yingshi.mobile.ui.settings.SettingsDialogs
import com.lemon.yingshi.mobile.util.setBackNavigation
import kotlinx.coroutines.launch

@dagger.hilt.android.AndroidEntryPoint
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding
    private val viewModel: AboutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setBackNavigation { finish() }
        setupActionCards()
        observeState()
        UpdateDownloadDialog.observe(
            activity = this,
            isDownloading = viewModel.isDownloading,
            downloadProgress = viewModel.downloadProgress,
            downloadFailed = viewModel.downloadFailed,
            installApk = viewModel.installApk
        )

        val versionCode = packageManager.getPackageInfo(packageName, 0).versionCode
        viewModel.checkForUpdates(versionCode)
    }

    private fun setupActionCards() {
        setupActionCard(
            binding.cardVersion,
            R.drawable.ic_about_update,
            getString(R.string.about_version_title),
            getString(R.string.about_version_checking)
        )
        setupActionCard(
            binding.cardStats,
            R.drawable.ic_about_stats,
            getString(R.string.about_stats_title),
            getString(R.string.about_playback_stats, "0秒")
        )

        binding.cardVersion.root.setOnClickListener { onVersionCardClick() }
        binding.cardStats.root.setOnClickListener { onStatsCardClick() }
    }

    private fun setupActionCard(
        cardBinding: ItemAboutActionCardBinding,
        iconRes: Int,
        title: String,
        subtitle: String
    ) {
        cardBinding.cardIcon.setImageResource(iconRes)
        cardBinding.cardTitle.text = title
        cardBinding.cardSubtitle.text = subtitle
    }

    private fun observeState() {
        val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isChecking.collect { checking ->
                    binding.cardVersion.cardSubtitle.text = if (checking) {
                        getString(R.string.about_version_checking)
                    } else {
                        formatVersionSubtitle(currentVersion)
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hasUpdate.collect {
                    binding.cardVersion.cardBadge.isVisible = it
                    if (!viewModel.isChecking.value) {
                        binding.cardVersion.cardSubtitle.text = formatVersionSubtitle(currentVersion)
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.versionInfo.collect {
                    if (!viewModel.isChecking.value) {
                        binding.cardVersion.cardSubtitle.text = formatVersionSubtitle(currentVersion)
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.totalPlaybackTimeMs.collect { totalMs ->
                    binding.cardStats.cardSubtitle.text = getString(
                        R.string.about_playback_stats,
                        formatPlaybackTime(totalMs)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        UpdateInstallCoordinator.retryPendingInstall(this)
    }

    override fun onDestroy() {
        UpdateDownloadDialog.dismiss()
        super.onDestroy()
    }

    private fun formatVersionSubtitle(currentVersion: String): String {
        val versionInfo = viewModel.versionInfo.value
        return if (viewModel.hasUpdate.value && versionInfo != null) {
            getString(
                R.string.about_version_with_update,
                currentVersion,
                versionInfo.versionName
            )
        } else {
            getString(R.string.about_version_stable, currentVersion)
        }
    }

    private fun onVersionCardClick() {
        if (viewModel.isDownloading.value) return
        val versionInfo = viewModel.versionInfo.value
        if (viewModel.hasUpdate.value && versionInfo != null) {
            AboutDialogs.showVersionUpdateDialog(this, versionInfo) {
                viewModel.downloadUpdate()
            }
            return
        }
        if (viewModel.isChecking.value) return
        Toast.makeText(this, R.string.about_update_latest, Toast.LENGTH_SHORT).show()
        val versionCode = packageManager.getPackageInfo(packageName, 0).versionCode
        viewModel.checkForUpdates(versionCode)
    }

    private fun onStatsCardClick() {
        SettingsDialogs.showConfirmDialog(
            context = this,
            message = getString(R.string.about_clear_stats_confirm)
        ) {
            viewModel.clearPlaybackStats()
            Toast.makeText(this, R.string.about_stats_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatPlaybackTime(totalMs: Long): String {
        val hours = totalMs / (1000 * 60 * 60)
        val minutes = (totalMs / (1000 * 60)) % 60
        val seconds = (totalMs / 1000) % 60
        return when {
            hours > 0 -> "${hours}小时${minutes}分钟"
            minutes > 0 -> "${minutes}分${seconds}秒"
            seconds > 0 -> "${seconds}秒"
            else -> "0秒"
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, AboutActivity::class.java)
    }
}

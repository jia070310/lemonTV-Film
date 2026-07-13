package com.lemon.yingshi.mobile.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityUserProfileBinding
import com.lemon.yingshi.mobile.databinding.ItemUserInfoRowBinding
import com.lemon.yingshi.tv.domain.service.MediaStorageHelper
import com.lemon.yingshi.tv.util.StorageFormatter
import com.lemon.yingshi.mobile.util.setBackNavigation
import com.lemon.yingshi.tv.domain.service.UserAvatarStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UserProfileActivity : AppCompatActivity() {

    @Inject lateinit var mediaStorageHelper: MediaStorageHelper
    @Inject lateinit var userAvatarStore: UserAvatarStore

    private lateinit var binding: ActivityUserProfileBinding
    private val viewModel: UserProfileViewModel by viewModels()

    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        viewModel.setAvatarFromUri(uri) { success ->
            if (!success) {
                Toast.makeText(this, R.string.user_profile_avatar_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setBackNavigation { finish() }
        setupStats()
        setupInfoRows()
        observeState()

        binding.nicknameRow.setOnClickListener { showEditNicknameDialog() }
        binding.avatarContainer.setOnClickListener { showAvatarOptionsDialog() }
    }

    override fun onResume() {
        super.onResume()
        refreshCacheStat()
    }

    private fun setupStats() {
        binding.statHistory.statLabel.text = getString(R.string.profile_stat_history)
        binding.statFavorite.statLabel.text = getString(R.string.profile_stat_favorite)
        binding.statCache.statLabel.text = getString(R.string.profile_stat_cache)
        binding.statOffline.statLabel.text = getString(R.string.user_profile_stat_offline)

        binding.statHistory.root.setOnClickListener {
            startActivity(WatchHistoryActivity.intent(this))
        }
        binding.statFavorite.root.setOnClickListener {
            startActivity(FavoritesActivity.intent(this))
        }
        binding.statCache.root.setOnClickListener {
            startActivity(CacheActivity.intent(this))
        }
        binding.statOffline.root.setOnClickListener {
            startActivity(OfflineDownloadsActivity.intent(this))
        }
    }

    private fun setupInfoRows() {
        bindInfoRow(binding.rowPlayback, R.string.user_profile_playback_time, "—")
        bindInfoRow(binding.rowVersion, R.string.user_profile_app_version, "—")
        bindInfoRow(binding.rowSite, R.string.user_profile_site_name, "—")
        bindInfoRow(binding.rowServer, R.string.user_profile_server_url, "—")
    }

    private fun bindInfoRow(row: ItemUserInfoRowBinding, labelRes: Int, value: String) {
        row.infoLabel.setText(labelRes)
        row.infoValue.text = value
    }

    private fun observeState() {
        val appVersion = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        binding.rowVersion.infoValue.text = "v$appVersion"

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val nickname = state.displayName.ifBlank {
                        getString(R.string.profile_user_name)
                    }
                    binding.nicknameText.text = nickname
                    binding.userSubtitle.text = getString(R.string.user_profile_subtitle)

                    UserAvatarUi.bind(
                        binding.avatarImage,
                        userAvatarStore,
                        R.drawable.ic_profile_avatar,
                        state.avatarRevision
                    )

                    binding.statHistory.statValue.text = state.historyCount.toString()
                    binding.statFavorite.statValue.text = state.favoriteCount.toString()
                    binding.statOffline.statValue.text =
                        if (state.offlineSummary.totalStorageBytes > 0L) {
                            StorageFormatter.format(state.offlineSummary.totalStorageBytes)
                        } else {
                            state.offlineSummary.completedCount.toString()
                        }

                    binding.rowPlayback.infoValue.text =
                        formatPlaybackTime(state.playbackTimeMs)
                    binding.rowSite.infoValue.text = state.siteName.ifBlank {
                        getString(R.string.user_profile_not_configured)
                    }
                    binding.rowServer.infoValue.text = state.serverUrl.ifBlank {
                        getString(R.string.user_profile_not_configured)
                    }
                }
            }
        }
    }

    private fun refreshCacheStat() {
        binding.statCache.statValue.text =
            StorageFormatter.format(mediaStorageHelper.getTotalCacheSizeBytes())
    }

    private fun showAvatarOptionsDialog() {
        val options = mutableListOf(getString(R.string.user_profile_pick_avatar))
        if (userAvatarStore.hasCustomAvatar()) {
            options.add(getString(R.string.user_profile_reset_avatar))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.user_profile_change_avatar)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> pickAvatarLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    1 -> viewModel.clearAvatar { }
                }
            }
            .show()
    }

    private fun showEditNicknameDialog() {
        val currentName = binding.nicknameText.text?.toString().orEmpty()
        val input = EditText(this).apply {
            setText(currentName)
            setSelection(text?.length ?: 0)
            hint = getString(R.string.user_profile_nickname_hint)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.user_profile_edit_nickname)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    viewModel.updateDisplayName(name)
                }
            }
            .show()
    }

    private fun formatPlaybackTime(totalMs: Long): String {
        val hours = totalMs / (1000 * 60 * 60)
        val minutes = (totalMs / (1000 * 60)) % 60
        val seconds = (totalMs / 1000) % 60
        return when {
            hours > 0 -> "${hours}小时${minutes}分钟"
            minutes > 0 -> "${minutes}分${seconds}秒"
            seconds > 0 -> "${seconds}秒"
            else -> getString(R.string.user_profile_playback_empty)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        fun intent(context: Context): Intent = Intent(context, UserProfileActivity::class.java)
    }
}

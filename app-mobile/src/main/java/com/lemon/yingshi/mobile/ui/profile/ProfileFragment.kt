package com.lemon.yingshi.mobile.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.lemon.yingshi.mobile.databinding.LayoutProfileStatBinding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.tv.data.preferences.UserProfilePreferences
import com.lemon.yingshi.tv.domain.service.UserAvatarStore
import com.lemon.yingshi.mobile.databinding.FragmentProfileBinding
import com.lemon.yingshi.mobile.databinding.ItemProfileMenuBinding
import com.lemon.yingshi.mobile.ui.SettingsActivity
import com.lemon.yingshi.mobile.ui.VersionUpdateViewModel
import com.lemon.yingshi.tv.domain.service.MediaStorageHelper
import com.lemon.yingshi.tv.util.StorageFormatter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()
    private val versionUpdateViewModel: VersionUpdateViewModel by activityViewModels()

    @Inject lateinit var mediaStorageHelper: MediaStorageHelper
    @Inject lateinit var userProfilePreferences: UserProfilePreferences
    @Inject lateinit var userAvatarStore: UserAvatarStore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.statHistory.statLabel.text = getString(R.string.profile_stat_history)
        binding.statCache.statLabel.text = getString(R.string.profile_stat_cache)
        binding.statFavorite.statLabel.text = getString(R.string.profile_stat_favorite)

        binding.userHeader.setOnClickListener {
            startActivity(UserProfileActivity.intent(requireContext()))
        }

        binding.statHistory.root.setOnClickListener {
            startActivity(WatchHistoryActivity.intent(requireContext()))
        }
        binding.statCache.root.setOnClickListener {
            startActivity(CacheActivity.intent(requireContext()))
        }
        binding.statFavorite.root.setOnClickListener {
            startActivity(FavoritesActivity.intent(requireContext()))
        }

        setupMenu(binding.menuCache, R.drawable.ic_nav_recommend, getString(R.string.profile_menu_cache)) {
            startActivity(CacheActivity.intent(requireContext()))
        }
        setupMenu(binding.menuHistory, android.R.drawable.ic_menu_recent_history, getString(R.string.profile_menu_history)) {
            startActivity(WatchHistoryActivity.intent(requireContext()))
        }
        setupMenu(binding.menuFavorite, android.R.drawable.btn_star_big_off, getString(R.string.profile_menu_favorite)) {
            startActivity(FavoritesActivity.intent(requireContext()))
        }
        setupMenu(binding.menuOffline, android.R.drawable.stat_sys_download, getString(R.string.profile_menu_offline)) {
            startActivity(OfflineDownloadsActivity.intent(requireContext()))
        }
        setupMenu(binding.menuSettings, android.R.drawable.ic_menu_preferences, getString(R.string.profile_menu_settings)) {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        setupMenu(binding.menuAbout, android.R.drawable.ic_menu_info_details, getString(R.string.profile_menu_about)) {
            startActivity(AboutActivity.intent(requireContext()))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userProfilePreferences.avatarRevision.collect { revision ->
                    UserAvatarUi.bind(
                        binding.avatarImage,
                        userAvatarStore,
                        R.drawable.ic_profile_avatar,
                        revision
                    )
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userProfilePreferences.displayName.collect { name ->
                    binding.userNameText.text = name.ifBlank {
                        getString(R.string.profile_user_name)
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.historyCount.collect { count ->
                    binding.statHistory.statValue.text = count.toString()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.favoriteCount.collect { count ->
                    binding.statFavorite.statValue.text = count.toString()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.offlineSummary.collect { summary ->
                    binding.menuOffline.menuSubtitle.visibility = View.VISIBLE
                    binding.menuOffline.menuSubtitle.text =
                        OfflineDownloadUi.formatCacheDesc(requireContext(), summary)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                versionUpdateViewModel.hasUpdate.collect { hasUpdate ->
                    binding.menuAbout.menuBadge.isVisible = hasUpdate
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCacheStat()
    }

    private fun refreshCacheStat() {
        val bytes = mediaStorageHelper.getTotalCacheSizeBytes()
        binding.statCache.statValue.text = StorageFormatter.format(bytes)
        binding.menuCache.menuSubtitle.visibility = View.VISIBLE
        binding.menuCache.menuSubtitle.text = StorageFormatter.format(bytes)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

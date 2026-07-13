package com.lemon.yingshi.mobile.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityMainBinding
import com.lemon.yingshi.tv.ui.screens.home.MacCmsHomeUiState
import com.lemon.yingshi.tv.ui.screens.home.MacCmsHomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val versionUpdateViewModel: VersionUpdateViewModel by viewModels()
    private val homeViewModel: MacCmsHomeViewModel by viewModels()

    private val topLevelDestinations = setOf(
        R.id.homeFragment,
        R.id.recommendationFragment,
        R.id.profileFragment
    )

    private var splashDismissed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.splashOverlay.isVisible = true
        applySystemBarInsets()
        observeProfileNavBadge()
        observeUpdateDownload()
        observeHomeReadyForSplash()

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)
        syncBottomNavWithDestination()

        binding.bottomNav.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.homeFragment) {
                scrollHomeToTop()
            }
        }

        window.decorView.post {
            window.setBackgroundDrawableResource(R.color.background_dark)
        }

        lifecycleScope.launch {
            delay(500)
            val versionCode = packageManager.getPackageInfo(packageName, 0).versionCode
            versionUpdateViewModel.checkForUpdates(versionCode)
        }
    }

    private fun observeHomeReadyForSplash() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    delay(SPLASH_MAX_WAIT_MS)
                    dismissSplash()
                }
                homeViewModel.uiState.collect { state ->
                    if (isHomeReadyForDisplay(state)) {
                        dismissSplash()
                    }
                }
            }
        }
    }

    private fun dismissSplash() {
        if (splashDismissed) return
        splashDismissed = true
        binding.splashOverlay.isVisible = false
    }

    private fun isHomeReadyForDisplay(state: MacCmsHomeUiState): Boolean {
        if (state.isLoading) return false
        if (!state.isConfigured) return true
        if (state.error != null &&
            state.sections.isEmpty() &&
            state.recommendedItems.isEmpty()
        ) {
            return true
        }
        if (state.recommendedItems.isNotEmpty() || state.sections.isNotEmpty()) {
            return true
        }
        return !state.isLoadingSections
    }

    private fun observeUpdateDownload() {
        UpdateDownloadDialog.observe(
            activity = this,
            isDownloading = versionUpdateViewModel.isDownloading,
            downloadProgress = versionUpdateViewModel.downloadProgress,
            downloadFailed = versionUpdateViewModel.downloadFailed
        )
    }

    override fun onDestroy() {
        UpdateDownloadDialog.dismiss()
        super.onDestroy()
    }

    private fun observeProfileNavBadge() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                versionUpdateViewModel.hasUpdate.collect { hasUpdate ->
                    val badge = binding.bottomNav.getOrCreateBadge(R.id.profileFragment)
                    badge.isVisible = hasUpdate
                    if (hasUpdate) {
                        badge.backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.error_red)
                        badge.clearNumber()
                    }
                }
            }
        }
    }

    private fun syncBottomNavWithDestination() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id !in topLevelDestinations) return@addOnDestinationChangedListener
            val menuItem = binding.bottomNav.menu.findItem(destination.id) ?: return@addOnDestinationChangedListener
            if (!menuItem.isChecked) {
                menuItem.isChecked = true
            }
        }
    }

    private fun scrollHomeToTop() {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val home = navHost?.childFragmentManager?.fragments
            ?.filterIsInstance<com.lemon.yingshi.mobile.ui.home.HomeFragment>()
            ?.firstOrNull()
        home?.scrollToTop()
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navHostFragment.updatePadding(top = systemBars.top)
            binding.bottomNav.updatePadding(bottom = systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private companion object {
        const val SPLASH_MAX_WAIT_MS = 12_000L
    }
}

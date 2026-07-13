package com.lemon.yingshi.mobile.ui.settings

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityResourceSettingsBinding
import com.lemon.yingshi.tv.data.remote.model.MacCmsConnectionResult
import com.lemon.yingshi.tv.ui.viewmodel.MacCmsConfigViewModel
import com.lemon.yingshi.mobile.util.setBackNavigation
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ResourceSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResourceSettingsBinding
    private val viewModel: MacCmsConfigViewModel by viewModels()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResourceSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setBackNavigation { finish() }
        observeState()
        binding.testButton.setOnClickListener {
            viewModel.testConnection(binding.urlInput.text?.toString().orEmpty())
        }
        binding.saveButton.setOnClickListener {
            viewModel.saveServerUrl(binding.urlInput.text?.toString().orEmpty())
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.serverUrl.collect { url ->
                    if (binding.urlInput.text?.toString().orEmpty() != url) {
                        binding.urlInput.setText(url)
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.lastTestStatus,
                    viewModel.testResult,
                    viewModel.isTesting
                ) { status, result, testing ->
                    Triple(status, result, testing)
                }.collect { (status, result, testing) ->
                    binding.testButton.isEnabled = !testing
                    binding.saveButton.isEnabled = !testing
                    binding.testButton.text = if (testing) {
                        getString(R.string.settings_testing)
                    } else {
                        getString(R.string.settings_test_connectivity)
                    }
                    updateStatusUi(testing, status, result)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lastTestTime.collect { time ->
                    binding.lastTestText.isVisible = time > 0L
                    if (time > 0L) {
                        binding.lastTestText.text =
                            getString(R.string.settings_last_test_format, dateFormat.format(Date(time)))
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveMessage.collect { message ->
                    binding.saveMessageText.isVisible = !message.isNullOrBlank()
                    binding.saveMessageText.text = message
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    combine(
                        viewModel.lastTestStatus,
                        viewModel.siteName,
                        viewModel.maccmsVersion
                    ) { status, siteName, version -> Triple(status, siteName, version) },
                    combine(
                        viewModel.savedCategoryCount,
                        viewModel.savedApiSource,
                        viewModel.testResult
                    ) { categoryCount, apiSource, testResult ->
                        Triple(categoryCount, apiSource, testResult)
                    }
                ) { meta, details ->
                    ServerInfoState(
                        lastTestStatus = meta.first,
                        siteName = meta.second,
                        version = meta.third,
                        categoryCount = details.first,
                        apiSource = details.second,
                        testResult = details.third
                    )
                }.collect { state ->
                    updateServerInfoCard(state)
                }
            }
        }
    }

    private fun updateStatusUi(
        isTesting: Boolean,
        lastTestStatus: String,
        testResult: MacCmsConnectionResult?
    ) {
        val isConnected = lastTestStatus == "已连接"
        val dotDrawable = when {
            isTesting -> R.drawable.bg_status_dot_yellow
            isConnected -> R.drawable.bg_status_dot_green
            else -> R.drawable.bg_status_dot_gray
        }
        binding.statusDot.background = ContextCompat.getDrawable(this, dotDrawable)
        binding.statusText.text = when {
            isTesting -> getString(R.string.settings_status_testing)
            !testResult?.message.isNullOrBlank() -> testResult!!.message
            lastTestStatus.isNotBlank() -> lastTestStatus
            else -> getString(R.string.settings_status_disconnected)
        }
    }

    private fun updateServerInfoCard(state: ServerInfoState) {
        val isConnected = state.lastTestStatus == "已连接"
        binding.serverInfoCard.isVisible = isConnected
        if (!isConnected) return

        val inputUrl = binding.urlInput.text?.toString().orEmpty()
        val displaySite = state.siteName.ifBlank { inputUrl }
        val displayCategoryCount = maxOf(
            state.testResult?.categoryCount ?: 0,
            state.categoryCount
        )
        val displayVersion = state.testResult?.maccmsVersionLabel?.takeIf { it.isNotBlank() }
            ?: state.version.ifBlank { "—" }
        val displayApiSource = state.testResult?.apiSourceLabel?.takeIf { it.isNotBlank() }
            ?: state.apiSource.ifBlank { "—" }

        binding.infoSiteText.text = getString(R.string.settings_info_site_format, displaySite)
        binding.infoVersionText.text = getString(R.string.settings_info_version_format, displayVersion)
        binding.infoCategoryCountText.text =
            getString(R.string.settings_info_category_count_format, displayCategoryCount)
        binding.infoCategorySourceText.text =
            getString(R.string.settings_info_category_source_format, displayApiSource)
    }

    private data class ServerInfoState(
        val lastTestStatus: String,
        val siteName: String,
        val version: String,
        val categoryCount: Int,
        val apiSource: String,
        val testResult: MacCmsConnectionResult?
    )
}

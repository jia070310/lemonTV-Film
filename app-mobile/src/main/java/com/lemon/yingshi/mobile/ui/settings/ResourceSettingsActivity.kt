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
import com.lemon.yingshi.mobile.databinding.ItemMacCmsServerBinding
import com.lemon.yingshi.tv.data.remote.model.MacCmsConnectionResult
import com.lemon.yingshi.tv.data.remote.model.MacCmsServerEntry
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
            viewModel.saveServerUrl(
                binding.urlInput.text?.toString().orEmpty(),
                binding.nameInput.text?.toString().orEmpty()
            )
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.serverList,
                    viewModel.serverUrl,
                    viewModel.testingUrl
                ) { list, current, testing ->
                    Triple(list, current, testing)
                }.collect { (list, current, testing) ->
                    if (current.isNotBlank() && binding.urlInput.text?.toString().orEmpty() != current &&
                        !binding.urlInput.hasFocus()
                    ) {
                        binding.urlInput.setText(current)
                    }
                    val name = list.find { it.url == current }?.name.orEmpty()
                    if (!binding.nameInput.hasFocus() &&
                        binding.nameInput.text?.toString().orEmpty() != name
                    ) {
                        binding.nameInput.setText(name)
                    }
                    renderServerList(list, current, testing)
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
    }

    private fun updateStatusUi(
        isTesting: Boolean,
        lastTestStatus: String,
        testResult: MacCmsConnectionResult?
    ) {
        val isConnected = testResult?.success == true || lastTestStatus == "已连接"
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

    private fun renderServerList(
        list: List<MacCmsServerEntry>,
        currentUrl: String,
        testingUrl: String = ""
    ) {
        binding.savedServersTitle.isVisible = list.isNotEmpty()
        binding.serverListContainer.removeAllViews()
        list.forEach { entry ->
            val item = ItemMacCmsServerBinding.inflate(
                layoutInflater,
                binding.serverListContainer,
                false
            )
            val active = entry.url == currentUrl
            val title = buildString {
                append(entry.name.ifBlank { entry.url })
                if (active) append("  · ${getString(R.string.settings_server_current)}")
            }
            item.serverUrlText.text = title
            val subtitle = buildList {
                if (entry.name.isNotBlank()) add(entry.url)
                add(entry.compactSummary().ifBlank { "未测试" })
            }.joinToString(" · ")
            item.serverStatusText.text = subtitle
            item.serverStatusText.isVisible = true
            item.root.setBackgroundResource(
                if (active) R.drawable.bg_server_row_active else R.drawable.bg_settings_input
            )
            val rowTesting = testingUrl == entry.url
            item.testItemButton.isEnabled = testingUrl.isBlank()
            item.testItemButton.text = if (rowTesting) {
                getString(R.string.settings_testing)
            } else {
                getString(R.string.settings_test_list)
            }
            item.testItemButton.setOnClickListener { viewModel.testConnection(entry.url) }
            item.useButton.isVisible = !active
            item.useButton.setOnClickListener {
                binding.urlInput.setText(entry.url)
                binding.nameInput.setText(entry.name)
                viewModel.selectServer(entry.url)
            }
            item.deleteButton.setOnClickListener {
                val label = entry.name.ifBlank { entry.url }
                SettingsDialogs.showConfirmDialog(
                    this,
                    getString(R.string.settings_delete_server_confirm, label)
                ) { viewModel.removeServer(entry.url) }
            }
            binding.serverListContainer.addView(item.root)
        }
    }
}

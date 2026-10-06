package com.lemon.yingshi.mobile.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityPrivacySettingsBinding
import com.lemon.yingshi.mobile.databinding.ItemProfileMenuBinding
import com.lemon.yingshi.mobile.util.setBackNavigation
import com.lemon.yingshi.tv.data.remote.model.MacCmsServerEntry
import com.lemon.yingshi.tv.ui.viewmodel.PrivacySettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PrivacySettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacySettingsBinding
    private val viewModel: PrivacySettingsViewModel by viewModels()
    private var hideDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setBackNavigation { finish() }
        setupMenus()
    }

    private fun setupMenus() {
        setupMenu(
            binding.menuKeywords,
            android.R.drawable.ic_menu_edit,
            getString(R.string.settings_privacy_keywords)
        ) { openKeywordsDialog() }

        setupMenu(
            binding.menuHideCategories,
            android.R.drawable.ic_menu_view,
            getString(R.string.settings_privacy_hide)
        ) { openHideDialog() }

        setupMenu(
            binding.menuClearPrivacy,
            android.R.drawable.ic_menu_delete,
            getString(R.string.settings_privacy_clear)
        ) {
            val target = viewModel.activeServerUrl.value
            SettingsDialogs.showConfirmDialog(
                context = this,
                message = getString(
                    R.string.settings_privacy_clear_confirm,
                    target.ifBlank { getString(R.string.settings_privacy) }
                )
            ) {
                viewModel.clearAll(target)
                Toast.makeText(this, R.string.settings_cleared, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun servers(): List<MacCmsServerEntry> = viewModel.serverList.value

    private fun defaultServerUrl(): String {
        val list = servers()
        val urls = list.map { it.url }
        val active = viewModel.activeServerUrl.value
        return when {
            active.isNotBlank() && active in urls -> active
            else -> urls.firstOrNull().orEmpty()
        }
    }

    private fun openKeywordsDialog() {
        val list = servers()
        if (list.isEmpty()) {
            Toast.makeText(this, R.string.settings_privacy_no_server, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val selected = defaultServerUrl().ifBlank { list.first().url }
            val current = viewModel.keywordsFor(selected)
            SettingsDialogs.showPrivacyKeywordsDialog(
                context = this@PrivacySettingsActivity,
                currentKeywords = current,
                servers = list,
                selectedServerUrl = selected,
                onServerSelected = { url, applyKeywords ->
                    lifecycleScope.launch {
                        applyKeywords(viewModel.keywordsFor(url))
                    }
                }
            ) { url, raw ->
                viewModel.saveFilterKeywords(url, raw)
                Toast.makeText(this@PrivacySettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openHideDialog(initialUrl: String = defaultServerUrl()) {
        val list = servers()
        if (list.isEmpty()) {
            Toast.makeText(this, R.string.settings_privacy_no_server, Toast.LENGTH_SHORT).show()
            return
        }
        val selected = initialUrl.ifBlank { list.first().url }
        hideDialog?.dismiss()
        val loadingDialog = SettingsDialogs.showPrivacyHideDialog(
            context = this,
            items = emptyList(),
            isLoading = true,
            errorMessage = null,
            servers = list,
            selectedServerUrl = selected,
            onServerSelected = { url -> openHideDialog(url) },
            onSave = {}
        )
        hideDialog = loadingDialog
        lifecycleScope.launch {
            val result = viewModel.loadHideCandidates(selected)
            if (hideDialog !== loadingDialog) return@launch
            loadingDialog.dismiss()
            result.fold(
                onSuccess = { rows ->
                    val items = rows.map { row ->
                        SettingsDialogs.CategorySortItem(
                            key = row.typeId.toString(),
                            name = row.displayName,
                            visible = !row.hidden,
                            parentKey = row.parentTypeId?.toString(),
                            childKeys = row.childTypeIds.map { it.toString() }
                        )
                    }
                    if (items.isEmpty()) {
                        Toast.makeText(
                            this@PrivacySettingsActivity,
                            R.string.settings_privacy_hide_empty,
                            Toast.LENGTH_SHORT
                        ).show()
                        return@fold
                    }
                    hideDialog = SettingsDialogs.showPrivacyHideDialog(
                        context = this@PrivacySettingsActivity,
                        items = items,
                        isLoading = false,
                        errorMessage = null,
                        servers = list,
                        selectedServerUrl = selected,
                        onServerSelected = { url -> openHideDialog(url) },
                        onSave = { saved ->
                            val hiddenIds = saved
                                .filterNot { it.visible }
                                .flatMap { item ->
                                    listOfNotNull(item.key.toIntOrNull()) +
                                        item.childKeys.mapNotNull { it.toIntOrNull() }
                                }
                                .toSet()
                            viewModel.saveHiddenTypeIds(selected, hiddenIds)
                            Toast.makeText(
                                this@PrivacySettingsActivity,
                                R.string.settings_saved,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                },
                onFailure = { error ->
                    Toast.makeText(
                        this@PrivacySettingsActivity,
                        error.message ?: getString(R.string.settings_privacy_hide_empty),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
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
        menuBinding.root.setOnClickListener { onClick() }
    }
}

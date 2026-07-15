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
import com.lemon.yingshi.mobile.databinding.ActivityPrivacySettingsBinding
import com.lemon.yingshi.mobile.databinding.ItemProfileMenuBinding
import com.lemon.yingshi.mobile.util.setBackNavigation
import com.lemon.yingshi.tv.ui.viewmodel.PrivacySettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PrivacySettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacySettingsBinding
    private val viewModel: PrivacySettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setBackNavigation { finish() }
        setupMenus()
        observeSummaries()
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
            SettingsDialogs.showConfirmDialog(
                context = this,
                message = getString(R.string.settings_privacy_clear_confirm)
            ) {
                viewModel.clearAll()
                Toast.makeText(this, R.string.settings_cleared, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeSummaries() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.filterKeywordsRaw,
                    viewModel.hiddenTypeIds
                ) { keywords, hidden -> keywords to hidden }
                    .collect { (keywords, hidden) ->
                        binding.menuKeywords.menuSubtitle.visibility = View.VISIBLE
                        binding.menuKeywords.menuSubtitle.text = if (keywords.isBlank()) {
                            getString(R.string.settings_privacy_keywords_empty)
                        } else {
                            getString(
                                R.string.settings_privacy_keywords_current,
                                keywords.take(40) + if (keywords.length > 40) "…" else ""
                            )
                        }
                        binding.menuHideCategories.menuSubtitle.visibility = View.VISIBLE
                        binding.menuHideCategories.menuSubtitle.text = if (hidden.isEmpty()) {
                            getString(R.string.settings_privacy_hide_desc)
                        } else {
                            getString(R.string.settings_privacy_hide_count, hidden.size)
                        }
                        binding.menuClearPrivacy.menuSubtitle.visibility = View.GONE
                    }
            }
        }
    }

    private fun openKeywordsDialog() {
        lifecycleScope.launch {
            val current = viewModel.filterKeywordsRaw.first()
            SettingsDialogs.showPrivacyKeywordsDialog(
                context = this@PrivacySettingsActivity,
                currentKeywords = current
            ) { raw ->
                viewModel.saveFilterKeywords(raw)
                Toast.makeText(this@PrivacySettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openHideDialog() {
        val loadingDialog = SettingsDialogs.showPrivacyHideDialog(
            context = this,
            items = emptyList(),
            isLoading = true,
            errorMessage = null,
            onSave = {}
        )
        lifecycleScope.launch {
            val result = viewModel.loadHideCandidates()
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
                    SettingsDialogs.showPrivacyHideDialog(
                        context = this@PrivacySettingsActivity,
                        items = items,
                        isLoading = false,
                        errorMessage = null,
                        onSave = { saved ->
                            // 一级关闭时其子类已一并关闭；再扩展一次确保持久化完整
                            val hiddenIds = saved
                                .filterNot { it.visible }
                                .flatMap { item ->
                                    listOfNotNull(item.key.toIntOrNull()) +
                                        item.childKeys.mapNotNull { it.toIntOrNull() }
                                }
                                .toSet()
                            viewModel.saveHiddenTypeIds(hiddenIds)
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

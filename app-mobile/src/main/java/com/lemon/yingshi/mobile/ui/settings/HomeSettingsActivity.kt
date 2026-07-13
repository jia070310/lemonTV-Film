package com.lemon.yingshi.mobile.ui.settings



import android.os.Bundle

import android.view.View

import android.widget.Toast

import androidx.activity.viewModels

import androidx.appcompat.app.AppCompatActivity

import androidx.lifecycle.lifecycleScope

import com.lemon.yingshi.mobile.R

import com.lemon.yingshi.mobile.databinding.ActivityHomeSettingsBinding

import com.lemon.yingshi.mobile.databinding.ItemProfileMenuBinding

import com.lemon.yingshi.mobile.util.setBackNavigation
import dagger.hilt.android.AndroidEntryPoint

import kotlinx.coroutines.launch



@AndroidEntryPoint

class HomeSettingsActivity : AppCompatActivity() {



    private lateinit var binding: ActivityHomeSettingsBinding

    private val viewModel: HomeSettingsViewModel by viewModels()



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityHomeSettingsBinding.inflate(layoutInflater)

        setContentView(binding.root)



        binding.toolbar.setBackNavigation { finish() }

        setupMenu(

            binding.menuHomeSort,

            R.drawable.ic_settings_sort,

            getString(R.string.settings_home_sort)

        ) {

            openSortDialog()

        }

        setupMenu(

            binding.menuClearHomeCache,

            android.R.drawable.ic_menu_delete,

            getString(R.string.settings_clear_home_cache)

        ) {

            SettingsDialogs.showConfirmDialog(

                context = this,

                message = getString(R.string.settings_clear_home_cache_confirm)

            ) {

                viewModel.clearHomeCategoryCache()

                Toast.makeText(this, R.string.settings_cleared, Toast.LENGTH_SHORT).show()

            }

        }

    }



    private fun openSortDialog() {

        val loadingDialog = SettingsDialogs.showCategorySortDialog(

            context = this,

            items = emptyList(),

            isLoading = true,

            errorMessage = null,

            onSave = {}

        )

        lifecycleScope.launch {

            val (items, error) = viewModel.loadCategorySortAndGet()

            loadingDialog.dismiss()

            if (error != null && items.isEmpty()) {

                Toast.makeText(this@HomeSettingsActivity, error, Toast.LENGTH_SHORT).show()

                return@launch

            }

            SettingsDialogs.showCategorySortDialog(

                context = this@HomeSettingsActivity,

                items = items,

                isLoading = false,

                errorMessage = error,

                onSave = { saved ->

                    viewModel.saveCategorySort(saved)

                    Toast.makeText(this@HomeSettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()

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

        menuBinding.menuSubtitle.visibility = View.GONE

        menuBinding.root.setOnClickListener { onClick() }

    }

}


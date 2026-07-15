package com.lemon.yingshi.mobile.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivitySettingsBinding
import com.lemon.yingshi.mobile.databinding.ItemProfileMenuBinding
import com.lemon.yingshi.mobile.ui.settings.HomeSettingsActivity
import com.lemon.yingshi.mobile.ui.settings.PlaybackSettingsActivity
import com.lemon.yingshi.mobile.ui.settings.PrivacySettingsActivity
import com.lemon.yingshi.mobile.ui.settings.ResourceSettingsActivity
import com.lemon.yingshi.mobile.util.setBackNavigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setBackNavigation { finish() }
        setupMenu(
            binding.menuResource,
            android.R.drawable.ic_menu_manage,
            getString(R.string.settings_resource),
            getString(R.string.settings_resource_menu_desc)
        ) {
            startActivity(Intent(this, ResourceSettingsActivity::class.java))
        }
        setupMenu(
            binding.menuHome,
            android.R.drawable.ic_menu_sort_by_size,
            getString(R.string.settings_home),
            getString(R.string.settings_home_menu_desc)
        ) {
            startActivity(Intent(this, HomeSettingsActivity::class.java))
        }
        setupMenu(
            binding.menuPrivacy,
            android.R.drawable.ic_menu_view,
            getString(R.string.settings_privacy),
            getString(R.string.settings_privacy_menu_desc)
        ) {
            startActivity(Intent(this, PrivacySettingsActivity::class.java))
        }
        setupMenu(
            binding.menuPlayback,
            android.R.drawable.ic_media_play,
            getString(R.string.settings_playback),
            getString(R.string.settings_playback_menu_desc)
        ) {
            startActivity(Intent(this, PlaybackSettingsActivity::class.java))
        }
    }

    private fun setupMenu(
        menuBinding: ItemProfileMenuBinding,
        iconRes: Int,
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ) {
        menuBinding.menuIcon.setImageResource(iconRes)
        menuBinding.menuTitle.text = title
        menuBinding.menuSubtitle.visibility = View.VISIBLE
        menuBinding.menuSubtitle.text = subtitle
        menuBinding.root.setOnClickListener { onClick() }
    }
}

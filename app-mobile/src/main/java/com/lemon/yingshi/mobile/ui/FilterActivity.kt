package com.lemon.yingshi.mobile.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityFilterBinding
import com.lemon.yingshi.mobile.ui.filter.FilterCriteriaFragment
import com.lemon.yingshi.mobile.ui.filter.FilterResultsFragment
import com.lemon.yingshi.mobile.util.setBackNavigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FilterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFilterBinding

    private val libraryTitle: String? by lazy {
        intent.getStringExtra(EXTRA_LIBRARY_TITLE)?.takeIf { it.isNotBlank() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFilterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setBackNavigation { onBackPressedDispatcher.onBackPressed() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = supportFragmentManager.findFragmentById(R.id.filter_container)
                if (current is FilterCriteriaFragment && shouldStartWithResults()) {
                    showResults()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState == null) {
            val startWithResults = !libraryTitle.isNullOrBlank() ||
                intent.getIntExtra(EXTRA_TYPE_ID, -1) > 0 ||
                intent.getIntExtra(EXTRA_NAV_TYPE_ID, -1) > 0
            if (startWithResults) {
                showResults()
            } else {
                showCriteria()
            }
        }
    }

    fun showResults() {
        binding.toolbar.title = libraryTitle ?: getString(R.string.filter_results)
        supportFragmentManager.beginTransaction()
            .replace(R.id.filter_container, FilterResultsFragment())
            .commit()
    }

    fun showCriteria() {
        binding.toolbar.title = getString(R.string.filter_title)
        supportFragmentManager.beginTransaction()
            .replace(R.id.filter_container, FilterCriteriaFragment())
            .commit()
    }

    private fun shouldStartWithResults(): Boolean =
        !libraryTitle.isNullOrBlank() ||
            intent.getIntExtra(EXTRA_TYPE_ID, -1) > 0 ||
            intent.getIntExtra(EXTRA_NAV_TYPE_ID, -1) > 0

    companion object {
        const val EXTRA_TYPE_ID = "typeId"
        const val EXTRA_NAV_TYPE_ID = "navTypeId"
        const val EXTRA_LIBRARY_TITLE = "libraryTitle"

        fun intent(
            context: Context,
            typeId: Int = -1,
            navTypeId: Int = -1,
            libraryTitle: String? = null
        ): Intent {
            return Intent(context, FilterActivity::class.java).apply {
                if (typeId > 0) putExtra(EXTRA_TYPE_ID, typeId)
                if (navTypeId > 0) putExtra(EXTRA_NAV_TYPE_ID, navTypeId)
                libraryTitle?.takeIf { it.isNotBlank() }?.let {
                    putExtra(EXTRA_LIBRARY_TITLE, it)
                }
            }
        }

        fun movieLibraryIntent(context: Context, typeId: Int, navTypeId: Int = -1): Intent {
            return intent(
                context = context,
                typeId = typeId,
                navTypeId = navTypeId,
                libraryTitle = context.getString(R.string.movie_library)
            )
        }
    }
}

package com.lemon.yingshi.mobile.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivitySearchBinding
import com.lemon.yingshi.mobile.ui.home.PosterGridLayout
import com.lemon.yingshi.mobile.ui.home.VodGridAdapter
import com.lemon.yingshi.mobile.ui.search.SearchUiState
import com.lemon.yingshi.mobile.ui.search.SearchViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var adapter: VodGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = VodGridAdapter { vod ->
            com.lemon.yingshi.mobile.ui.VodUiBinder.openDetail(
                this,
                vod,
                viewModel::cacheVodForDetail
            )
        }
        val spacing = resources.getDimensionPixelSize(R.dimen.card_spacing)
        binding.resultsRecycler.apply {
            PosterGridLayout.setup(this, spacing)
            adapter = this@SearchActivity.adapter
            itemAnimator = null
        }

        binding.backButton.setOnClickListener { finish() }
        binding.clearButton.setOnClickListener { viewModel.clearSearch() }
        binding.clearHistoryButton.setOnClickListener { viewModel.clearHistory() }
        binding.prevPageButton.setOnClickListener {
            val state = viewModel.uiState.value as? SearchUiState.Success ?: return@setOnClickListener
            viewModel.goToPage(state.page - 1)
            binding.resultsRecycler.scrollToPosition(0)
        }
        binding.nextPageButton.setOnClickListener {
            val state = viewModel.uiState.value as? SearchUiState.Success ?: return@setOnClickListener
            viewModel.goToPage(state.page + 1)
            binding.resultsRecycler.scrollToPosition(0)
        }
        binding.goSettingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupSearchInput()
        observeState()

        binding.searchInput.post {
            binding.searchInput.requestFocus()
            showKeyboard(binding.searchInput)
        }
    }

    private fun setupSearchInput() {
        binding.searchInput.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            binding.clearButton.isVisible = query.isNotEmpty()
            viewModel.onSearchQueryChange(query)
        }
        binding.searchInput.setOnEditorActionListener { _, actionId, event ->
            val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isSearchAction) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun performSearch() {
        hideKeyboard()
        viewModel.search()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.searchQuery.collect { query ->
                        if (binding.searchInput.text?.toString() != query) {
                            binding.searchInput.setText(query)
                            binding.searchInput.setSelection(query.length)
                        }
                        binding.clearButton.isVisible = query.isNotEmpty()
                    }
                }
                launch {
                    viewModel.searchHistory.collect { history ->
                        renderHistory(history)
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        renderState(state)
                    }
                }
            }
        }
    }

    private fun renderHistory(history: List<String>) {
        binding.historyChipGroup.removeAllViews()
        binding.historyEmptyText.isVisible = history.isEmpty()
        binding.clearHistoryButton.isVisible = history.isNotEmpty()
        val inflater = LayoutInflater.from(this)
        history.forEach { query ->
            val chip = inflater.inflate(R.layout.view_filter_chip, binding.historyChipGroup, false) as TextView
            chip.text = query
            chip.setOnClickListener {
                viewModel.onSearchQueryChange(query)
                binding.searchInput.setText(query)
                binding.searchInput.setSelection(query.length)
                performSearch()
            }
            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = resources.getDimensionPixelSize(R.dimen.section_spacing) / 2
            binding.historyChipGroup.addView(chip, params)
        }
    }

    private fun renderState(state: SearchUiState) {
        binding.loadingIndicator.isVisible = state is SearchUiState.Loading
        binding.historyContainer.isVisible = state is SearchUiState.Initial
        binding.resultsContainer.isVisible = state is SearchUiState.Success
        binding.messageContainer.isVisible =
            state is SearchUiState.Empty || state is SearchUiState.Error

        when (state) {
            is SearchUiState.Success -> {
                binding.resultCount.text = getString(R.string.total_results, state.total)
                binding.pageIndicator.text = getString(
                    R.string.page_indicator,
                    state.page,
                    state.pageCount
                )
                binding.prevPageButton.isEnabled = state.page > 1
                binding.prevPageButton.alpha = if (state.page > 1) 1f else 0.4f
                binding.nextPageButton.isEnabled = state.page < state.pageCount
                binding.nextPageButton.alpha = if (state.page < state.pageCount) 1f else 0.4f
                binding.paginationBar.isVisible = state.pageCount > 1
                adapter.submitList(state.results)
            }
            is SearchUiState.Empty -> {
                binding.messageText.text = getString(
                    R.string.search_empty,
                    viewModel.searchQuery.value.trim()
                )
                binding.messageHint.text = getString(R.string.search_empty_hint)
                binding.messageHint.isVisible = true
                binding.goSettingsButton.isVisible = false
            }
            is SearchUiState.Error -> {
                binding.messageText.text = state.message
                binding.messageHint.text = getString(R.string.search_error_hint)
                binding.messageHint.isVisible = true
                binding.goSettingsButton.isVisible =
                    state.message.contains("MacCMS") || state.message.contains("服务器")
            }
            else -> Unit
        }
    }

    private fun showKeyboard(input: EditText) {
        input.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }
}

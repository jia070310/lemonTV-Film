package com.lemon.yingshi.mobile.ui.recommendation

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.util.TouchFeedback
import com.lemon.yingshi.mobile.databinding.FragmentRecommendationBinding
import com.lemon.yingshi.mobile.ui.SettingsActivity
import com.lemon.yingshi.mobile.ui.VodUiBinder
import com.lemon.yingshi.mobile.ui.home.PosterGridLayout
import com.lemon.yingshi.mobile.ui.home.VodGridAdapter
import com.lemon.yingshi.tv.ui.screens.recommended.RecommendedUiState
import com.lemon.yingshi.tv.ui.screens.recommended.RecommendedViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecommendationFragment : Fragment() {

    private var _binding: FragmentRecommendationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecommendedViewModel by viewModels()
    private lateinit var adapter: VodGridAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecommendationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = VodGridAdapter { vod ->
            VodUiBinder.openDetail(requireContext(), vod, viewModel::cacheVodForDetail)
        }
        val spacing = resources.getDimensionPixelSize(R.dimen.card_spacing)
        binding.recommendRecycler.apply {
            PosterGridLayout.setup(this, spacing)
            adapter = this@RecommendationFragment.adapter
            itemAnimator = null
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    binding.refreshLayout.isEnabled = !recyclerView.canScrollVertically(-1)
                    if (dy <= 0) return
                    val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    if (lastVisible >= (recyclerView.adapter?.itemCount ?: 0) - 4) {
                        viewModel.loadMore()
                    }
                }
            })
        }

        binding.refreshLayout.setColorSchemeResources(R.color.primary_yellow)
        binding.refreshLayout.setOnRefreshListener {
            viewModel.loadMobileFeed(forceRefresh = true)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isLoadingMore.collect { loading ->
                        binding.loadingMore.isVisible = loading
                    }
                }
                launch {
                    viewModel.isRefreshing.collect { refreshing ->
                        binding.refreshLayout.isRefreshing = refreshing
                    }
                }
                viewModel.uiState.collect { state ->
                    when (state) {
                        RecommendedUiState.Loading -> {
                            binding.loadingIndicator.isVisible = !binding.refreshLayout.isRefreshing
                            binding.recommendRecycler.isVisible = false
                            binding.emptyText.isVisible = false
                            binding.errorText.isVisible = false
                            binding.recommendCount.isVisible = false
                        }

                        is RecommendedUiState.Empty -> {
                            binding.loadingIndicator.isVisible = false
                            binding.recommendRecycler.isVisible = false
                            binding.emptyText.isVisible = true
                            binding.emptyText.text = state.message
                            binding.errorText.isVisible = false
                            binding.recommendCount.isVisible = false
                        }

                        is RecommendedUiState.Error -> {
                            binding.loadingIndicator.isVisible = false
                            binding.recommendRecycler.isVisible = false
                            binding.emptyText.isVisible = false
                            binding.errorText.isVisible = true
                            binding.errorText.text = state.message
                            binding.recommendCount.isVisible = false
                            if (state.message.contains("配置")) {
                                TouchFeedback.applyBorderlessRipple(binding.errorText)
                                binding.errorText.setOnClickListener {
                                    startActivity(Intent(requireContext(), SettingsActivity::class.java))
                                }
                            }
                        }

                        is RecommendedUiState.Success -> {
                            binding.loadingIndicator.isVisible = false
                            binding.recommendRecycler.isVisible = true
                            binding.emptyText.isVisible = false
                            binding.errorText.isVisible = false
                            binding.recommendCount.isVisible = true
                            binding.recommendCount.text = getString(
                                R.string.recommend_total_count,
                                state.total
                            )
                            adapter.submitList(state.items)
                        }
                    }
                }
            }
        }

        if (savedInstanceState == null) {
            viewModel.loadMobileFeed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

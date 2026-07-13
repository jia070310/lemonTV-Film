package com.lemon.yingshi.mobile.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.FragmentHomeBinding
import com.lemon.yingshi.mobile.databinding.LayoutHomeSectionBinding
import com.lemon.yingshi.mobile.ui.profile.OfflineDownloadUi
import com.lemon.yingshi.mobile.ui.profile.OfflineDownloadsActivity
import com.lemon.yingshi.mobile.ui.profile.UserAvatarUi
import com.lemon.yingshi.mobile.ui.profile.UserProfileActivity
import com.lemon.yingshi.tv.data.preferences.UserProfilePreferences
import com.lemon.yingshi.tv.domain.service.UserAvatarStore
import com.lemon.yingshi.mobile.ui.FilterActivity
import com.lemon.yingshi.mobile.ui.SearchActivity
import com.lemon.yingshi.mobile.ui.SettingsActivity
import com.lemon.yingshi.mobile.ui.VersionUpdateViewModel
import com.lemon.yingshi.mobile.ui.VodUiBinder
import com.lemon.yingshi.mobile.ui.navigation.TopLevelNavigation
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem
import com.lemon.yingshi.tv.domain.service.OfflineDownloadService
import com.lemon.yingshi.tv.ui.screens.home.HOME_RECOMMENDED_HOME_ITEMS
import com.lemon.yingshi.tv.ui.screens.home.HOME_SKELETON_CARD_COUNT
import com.lemon.yingshi.tv.ui.screens.home.MacCmsHomeSection
import com.lemon.yingshi.tv.ui.screens.home.MacCmsHomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MacCmsHomeViewModel by activityViewModels()
    private val versionUpdateViewModel: VersionUpdateViewModel by activityViewModels()
    private lateinit var recommendAdapter: VodGridAdapter

    @Inject lateinit var offlineDownloadService: OfflineDownloadService
    @Inject lateinit var userProfilePreferences: UserProfilePreferences
    @Inject lateinit var userAvatarStore: UserAvatarStore

    private val sectionBindings = linkedMapOf<String, LayoutHomeSectionBinding>()
    private val sectionAdapters = linkedMapOf<String, VodGridAdapter>()
    private var pendingVisibleLoad: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recommendAdapter = VodGridAdapter { vod ->
            VodUiBinder.openDetail(requireContext(), vod, viewModel::cacheVodForDetail)
        }
        binding.carouselSection.carouselRecycler.apply {
            setupHorizontalPosterRow(this, recommendAdapter)
        }

        binding.header.searchEntry.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
        binding.header.avatarEntry.setOnClickListener {
            startActivity(UserProfileActivity.intent(requireContext()))
        }
        binding.carouselSection.carouselMore.setOnClickListener {
            TopLevelNavigation.navigate(this, R.id.recommendationFragment)
        }
        binding.offlineCard.offlineEntry.setOnClickListener {
            startActivity(OfflineDownloadsActivity.intent(requireContext()))
        }

        binding.versionUpdateBanner.updateBannerIcon.setOnClickListener {
            versionUpdateViewModel.downloadUpdate()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    versionUpdateViewModel.showTopBanner.collect { show ->
                        binding.versionBannerContainer.isVisible = show
                    }
                }
                launch {
                    versionUpdateViewModel.versionInfo.collect { version ->
                        if (version != null) {
                            binding.versionUpdateBanner.updateBannerText.text =
                                getString(R.string.about_update_banner, version.versionName)
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                offlineDownloadService.observeDownloadSummary().collect { summary ->
                    binding.offlineCard.offlineDesc.text =
                        OfflineDownloadUi.formatCacheDesc(requireContext(), summary)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userProfilePreferences.avatarRevision.collect { revision ->
                    UserAvatarUi.bind(
                        binding.header.avatarEntry,
                        userAvatarStore,
                        R.drawable.ic_header_avatar,
                        revision
                    )
                }
            }
        }

        binding.refreshLayout.setOnRefreshListener {
            viewModel.loadHome(forceRefresh = true)
        }
        binding.scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            binding.refreshLayout.canRefresh = scrollY == 0
            scheduleVisibleSectionLoad()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isRefreshing.collect { refreshing ->
                        binding.refreshLayout.isRefreshing = refreshing
                    }
                }
                viewModel.uiState.collect { state ->
                    binding.loadingIndicator.isVisible = state.isLoading &&
                        state.sections.isEmpty() &&
                        state.recommendedItems.isEmpty() &&
                        !binding.refreshLayout.isRefreshing

                    if (!state.isConfigured) {
                        showEmpty(getString(R.string.configure_server_first)) {
                            startActivity(Intent(requireContext(), SettingsActivity::class.java))
                        }
                        return@collect
                    }

                    if (state.error != null &&
                        state.sections.isEmpty() &&
                        state.recommendedItems.isEmpty()
                    ) {
                        showEmpty(state.error.orEmpty()) {
                            viewModel.loadHome(forceRefresh = true)
                        }
                        return@collect
                    }

                    binding.emptyState.isVisible = false
                    binding.scrollView.isVisible = true

                    val recommended = state.recommendedItems.take(HOME_RECOMMENDED_HOME_ITEMS)
                    binding.carouselSection.root.isVisible = recommended.isNotEmpty()
                    recommendAdapter.submitListIfChanged(recommended)

                    renderSections(state.sections)
                    scheduleVisibleSectionLoad()
                }
            }
        }
    }

    private fun scheduleVisibleSectionLoad() {
        val scrollView = _binding?.scrollView ?: return
        pendingVisibleLoad?.let { scrollView.removeCallbacks(it) }
        val task = Runnable { loadVisibleSections() }
        pendingVisibleLoad = task
        scrollView.postDelayed(task, 150)
    }

    private fun loadVisibleSections() {
        val scrollView = _binding ?: return
        if (sectionBindings.isEmpty()) return
        val scrollY = scrollView.scrollView.scrollY
        val viewportBottom = scrollY + scrollView.scrollView.height
        val prefetchPx = resources.displayMetrics.heightPixels / 2
        val keysToLoad = sectionBindings.mapNotNull { (key, sectionBinding) ->
            val top = sectionBinding.root.top
            val bottom = sectionBinding.root.bottom
            if (bottom >= scrollY - prefetchPx && top <= viewportBottom + prefetchPx) key else null
        }
        viewModel.ensureSectionsLoaded(keysToLoad)
    }

    private fun showEmpty(message: String, action: () -> Unit) {
        binding.scrollView.isVisible = false
        binding.emptyState.isVisible = true
        binding.emptyMessage.text = message
        binding.emptyAction.setOnClickListener { action() }
    }

    private fun buildLibraryTitle(typeName: String): String {
        val normalized = typeName.trim()
        if (normalized.isBlank()) return getString(R.string.filter_results)
        if (normalized == "电影") return getString(R.string.movie_library)
        return if (normalized.endsWith("库")) normalized else "${normalized}库"
    }

    private fun renderSections(sections: List<MacCmsHomeSection>) {
        val container = binding.sectionsContainer
        val inflater = LayoutInflater.from(requireContext())

        val activeKeys = sections.map { it.sectionKey }.toSet()
        (sectionBindings.keys - activeKeys).forEach { key ->
            sectionBindings[key]?.root?.let { container.removeView(it) }
            sectionBindings.remove(key)
            sectionAdapters.remove(key)
        }

        sections.forEach { section ->
            val sectionBinding = sectionBindings.getOrPut(section.sectionKey) {
                LayoutHomeSectionBinding.inflate(inflater, container, false).also { binding ->
                    container.addView(binding.root)
                }
            }
            val sectionAdapter = sectionAdapters.getOrPut(section.sectionKey) {
                VodGridAdapter { vod ->
                    VodUiBinder.openDetail(requireContext(), vod, viewModel::cacheVodForDetail)
                }.also { adapter ->
                    setupHorizontalPosterRow(sectionBinding.sectionRecycler, adapter)
                }
            }

            sectionBinding.sectionTitle.text = section.typeName
            sectionBinding.sectionMore.setOnClickListener {
                val libraryTitle = buildLibraryTitle(section.typeName)
                startActivity(
                    FilterActivity.intent(
                        context = requireContext(),
                        typeId = section.typeId,
                        navTypeId = section.navTypeId ?: -1,
                        libraryTitle = libraryTitle
                    )
                )
            }

            when {
                section.items.isEmpty() && (section.isLoading || !section.isLoaded) ->
                    sectionAdapter.showSkeleton(HOME_SKELETON_CARD_COUNT)
                else -> sectionAdapter.submitList(section.items)
            }
        }

        // 保持栏目顺序与配置一致
        sections.forEachIndexed { index, section ->
            sectionBindings[section.sectionKey]?.root?.let { view ->
                if (container.indexOfChild(view) != index) {
                    container.removeView(view)
                    container.addView(view, index)
                }
            }
        }
    }

    private fun setupHorizontalPosterRow(recycler: RecyclerView, adapter: VodGridAdapter) {
        HorizontalPosterRowLayout.setup(recycler, adapter)
    }

    override fun onDestroyView() {
        pendingVisibleLoad?.let { _binding?.scrollView?.removeCallbacks(it) }
        pendingVisibleLoad = null
        sectionBindings.clear()
        sectionAdapters.clear()
        super.onDestroyView()
        _binding = null
    }

    fun scrollToTop() {
        val currentBinding = _binding ?: return
        currentBinding.scrollView.smoothScrollTo(0, 0)
    }
}

class VodGridAdapter(
    private val onClick: (MacCmsVodItem) -> Unit
) : RecyclerView.Adapter<VodGridAdapter.ViewHolder>() {

    private val items = mutableListOf<MacCmsVodItem>()
    private var skeletonMode = false
    private var skeletonCount = 0
    private var horizontalRow = false
    private var visibleColumns = 1
    private var itemSpacingPx = 0
    private var columnWidthPx = 0

    fun configureHorizontalRow(columns: Int, spacingPx: Int) {
        horizontalRow = true
        visibleColumns = columns.coerceAtLeast(1)
        itemSpacingPx = spacingPx
    }

    fun updateHorizontalWidth(recyclerWidth: Int) {
        if (!horizontalRow || recyclerWidth <= 0) return
        val totalSpacing = itemSpacingPx * (visibleColumns - 1)
        val newWidth = ((recyclerWidth - totalSpacing).coerceAtLeast(0) / visibleColumns)
        if (newWidth != columnWidthPx && newWidth > 0) {
            columnWidthPx = newWidth
            if (itemCount > 0) {
                notifyItemRangeChanged(0, itemCount, PAYLOAD_WIDTH)
            }
        }
    }

    fun submitList(data: List<MacCmsVodItem>) {
        submitListIfChanged(data)
    }

    fun submitListIfChanged(data: List<MacCmsVodItem>) {
        skeletonMode = false
        skeletonCount = 0
        if (items.size == data.size &&
            items.zip(data).all { (left, right) -> displayKey(left) == displayKey(right) }
        ) {
            return
        }
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    private fun displayKey(item: MacCmsVodItem): String =
        "${item.vodId}|${item.vodPic.orEmpty()}|${item.vodPicThumb.orEmpty()}|${item.vodName}"

    fun showSkeleton(count: Int) {
        skeletonMode = true
        skeletonCount = count.coerceAtLeast(1)
        items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vod_grid, parent, false)
        if (horizontalRow) {
            view.layoutParams = RecyclerView.LayoutParams(
                resolveColumnWidth(parent),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return ViewHolder(view)
    }

    private fun resolveColumnWidth(parent: ViewGroup): Int {
        if (columnWidthPx > 0) return columnWidthPx
        val recycler = parent as? RecyclerView ?: return ViewGroup.LayoutParams.WRAP_CONTENT
        val recyclerWidth = recycler.width.takeIf { it > 0 } ?: run {
            val padding = recycler.paddingLeft + recycler.paddingRight
            parent.resources.displayMetrics.widthPixels - padding
        }
        val totalSpacing = itemSpacingPx * (visibleColumns - 1)
        return ((recyclerWidth - totalSpacing).coerceAtLeast(0) / visibleColumns)
    }

    override fun getItemCount(): Int = if (skeletonMode) skeletonCount else items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        applyColumnWidth(holder)
        if (skeletonMode) {
            holder.bindSkeleton()
        } else {
            holder.bind(items[position], onClick)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_WIDTH)) {
            applyColumnWidth(holder)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    private fun applyColumnWidth(holder: ViewHolder) {
        if (horizontalRow && columnWidthPx > 0) {
            val params = holder.itemView.layoutParams
            if (params.width != columnWidthPx) {
                params.width = columnWidthPx
                holder.itemView.layoutParams = params
            }
        }
    }

    companion object {
        private const val PAYLOAD_WIDTH = "width"
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val poster = itemView.findViewById<android.widget.ImageView>(R.id.poster_image)
        private val title = itemView.findViewById<TextView>(R.id.title_text)
        private val ratingBadge = itemView.findViewById<View>(R.id.rating_badge)
        private val ratingText = itemView.findViewById<TextView>(R.id.rating_text)
        private val tags = itemView.findViewById<android.widget.LinearLayout>(R.id.tags_container)

        fun bind(vod: MacCmsVodItem, onClick: (MacCmsVodItem) -> Unit) {
            title.isVisible = true
            ratingBadge.isVisible = true
            tags.isVisible = true
            title.text = vod.vodName
            VodUiBinder.bindPoster(poster, vod)
            VodUiBinder.bindRating(ratingBadge, ratingText, vod.vodScore)
            VodUiBinder.bindTags(tags, vod)
            itemView.setOnClickListener { onClick(vod) }
        }

        fun bindSkeleton() {
            title.isVisible = false
            ratingBadge.isVisible = false
            tags.isVisible = false
            poster.setImageResource(R.drawable.bg_poster_card)
            itemView.setOnClickListener(null)
        }
    }
}

package com.lemon.yingshi.mobile.ui

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.os.Bundle
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityDetailBinding
import com.lemon.yingshi.mobile.ui.player.PlayerLauncher
import com.lemon.yingshi.tv.data.local.database.entity.OfflineDownloadStatus
import com.lemon.yingshi.tv.domain.service.OfflineDownloadService
import com.lemon.yingshi.tv.domain.model.MediaType
import com.lemon.yingshi.tv.ui.screens.detail.DetailUiState
import com.lemon.yingshi.tv.ui.screens.detail.DetailViewModel
import com.lemon.yingshi.tv.ui.screens.detail.EpisodeItem
import com.lemon.yingshi.tv.utils.EpisodeLabelFormatter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DetailActivity : AppCompatActivity() {

    @Inject lateinit var offlineDownloadService: OfflineDownloadService

    private lateinit var binding: ActivityDetailBinding
    private val viewModel: DetailViewModel by viewModels()
    private lateinit var episodeAdapter: EpisodeAdapter

    private var plotExpanded = false
    private var actorsExpanded = false
    private var selectedEpisodeId: String? = null
    private var episodeCurrentPage = 0
    private var lastEpisodeMediaId: String? = null
    private var lastPlaySourceIndex: Int? = null
    private var currentEpisodes: List<EpisodeItem> = emptyList()

    private val playerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 播放页关闭后回到详情页，无需额外处理
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        episodeAdapter = EpisodeAdapter { episode ->
            selectedEpisodeId = episode.id
            episodeAdapter.flashSelection(episode.id)
            refreshCacheButtonState()
            playEpisode(episode)
        }
        binding.episodeRecycler.apply {
            layoutManager = GridLayoutManager(this@DetailActivity, EPISODE_COLUMNS)
            adapter = episodeAdapter
            itemAnimator = null
            isNestedScrollingEnabled = false
        }

        val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID).orEmpty()
        binding.backButton.setOnClickListener { finish() }
        binding.plotToggle.setOnClickListener { togglePlot() }
        binding.actorsToggle.setOnClickListener { toggleActors() }
        binding.favoriteButton.setOnClickListener { viewModel.toggleFavorite() }
        binding.cacheButton.setOnClickListener { showOfflineCachePicker() }
        binding.playButton.setOnClickListener { playResume() }
        binding.episodePrevPage.setOnClickListener {
            if (episodeCurrentPage > 0) {
                episodeCurrentPage--
                renderEpisodePage()
            }
        }
        binding.episodeNextPage.setOnClickListener {
            val totalPages = episodeTotalPages(currentEpisodes.size)
            if (episodeCurrentPage < totalPages - 1) {
                episodeCurrentPage++
                renderEpisodePage()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        DetailUiState.Loading -> binding.loadingIndicator.isVisible = true
                        is DetailUiState.Error -> {
                            binding.loadingIndicator.isVisible = false
                            binding.detailTitle.text = state.message
                        }
                        is DetailUiState.Success -> renderSuccess(state)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isFavorite.collect { favorite ->
                    binding.favoriteButton.setImageResource(
                        if (favorite) R.drawable.ic_detail_favorite_filled else R.drawable.ic_detail_favorite
                    )
                    binding.favoriteLabel.text = getString(
                        if (favorite) R.string.detail_favorited else R.string.detail_favorite
                    )
                }
            }
        }

        viewModel.loadMediaDetail(mediaId)
    }

    private fun renderSuccess(state: DetailUiState.Success) {
        binding.loadingIndicator.isVisible = false
        val media = state.media
        binding.detailTitle.text = media.title
        binding.heroImage.load(media.backdropUrl ?: media.posterUrl) {
            crossfade(true)
            placeholder(R.drawable.bg_poster_card)
        }

        renderMetaRow(media.rating, media.year, media.genres, null)
        renderInfoGrid(state)
        renderActors(media.actors)
        binding.plotText.text = media.overview?.replace(Regex("<[^>]+>"), "").orEmpty()
        updatePlotToggleVisibility()
        renderPlaySources(state)

        if (state.playSources.isNotEmpty() &&
            lastPlaySourceIndex != null &&
            lastPlaySourceIndex != state.selectedPlaySourceIndex
        ) {
            episodeCurrentPage = 0
            selectedEpisodeId = null
        }
        lastPlaySourceIndex = state.selectedPlaySourceIndex

        val showEpisodes = state.episodes.isNotEmpty()
        binding.episodeSection.isVisible = showEpisodes
        if (showEpisodes) {
            if (media.id != lastEpisodeMediaId) {
                episodeCurrentPage = 0
                selectedEpisodeId = null
                lastEpisodeMediaId = media.id
                lastPlaySourceIndex = null
            }
            currentEpisodes = state.episodes.sortedBy { it.episodeNumber }
            if (selectedEpisodeId == null) {
                selectedEpisodeId = currentEpisodes.firstOrNull()?.id
            }
            val isMovieOptions = media.type == MediaType.MOVIE
            binding.episodeSectionTitle.text = getString(
                if (isMovieOptions) R.string.play_option_list else R.string.episode_list
            )
            if (isMovieOptions) {
                binding.episodeCount.text = getString(
                    R.string.play_option_count_format,
                    currentEpisodes.size
                )
            } else {
                val latestEpisode = max(
                    currentEpisodes.maxOfOrNull { it.episodeNumber } ?: 0,
                    currentEpisodes.size
                )
                val totalCount = media.totalEpisodes ?: latestEpisode
                binding.episodeCount.text = getString(
                    R.string.episode_count_format,
                    latestEpisode,
                    totalCount
                )
            }
            renderEpisodePage()
        } else {
            currentEpisodes = emptyList()
        }
        refreshCacheButtonState()
    }

    private fun renderPlaySources(state: DetailUiState.Success) {
        val sources = state.playSources
        binding.playSourceSection.isVisible = sources.isNotEmpty()
        binding.playSourceContainer.removeAllViews()
        if (sources.isEmpty()) return

        val inflater = LayoutInflater.from(this)
        sources.forEachIndexed { index, name ->
            val chip = inflater.inflate(R.layout.view_filter_chip, binding.playSourceContainer, false) as TextView
            chip.text = name
            chip.isSelected = index == state.selectedPlaySourceIndex
            chip.setOnClickListener {
                if (index != state.selectedPlaySourceIndex) {
                    viewModel.selectPlaySource(index)
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = resources.getDimensionPixelSize(R.dimen.card_spacing) / 2
            binding.playSourceContainer.addView(chip, params)
        }
    }

    private fun renderEpisodePage() {
        val totalPages = episodeTotalPages(currentEpisodes.size)
        episodeCurrentPage = episodeCurrentPage.coerceIn(0, totalPages - 1)

        val pageItems = currentEpisodes
            .drop(episodeCurrentPage * EPISODES_PER_PAGE)
            .take(EPISODES_PER_PAGE)

        episodeAdapter.submitEpisodes(pageItems, highlightId = null)

        val showPagination = totalPages > 1
        binding.episodePagination.isVisible = showPagination
        if (showPagination) {
            binding.episodePageText.text = getString(
                R.string.page_indicator,
                episodeCurrentPage + 1,
                totalPages
            )
            binding.episodePrevPage.isEnabled = episodeCurrentPage > 0
            binding.episodePrevPage.alpha = if (episodeCurrentPage > 0) 1f else 0.4f
            binding.episodeNextPage.isEnabled = episodeCurrentPage < totalPages - 1
            binding.episodeNextPage.alpha = if (episodeCurrentPage < totalPages - 1) 1f else 0.4f
        }
        refreshCacheButtonState()
    }

    private fun episodeTotalPages(episodeCount: Int): Int =
        max(1, (episodeCount + EPISODES_PER_PAGE - 1) / EPISODES_PER_PAGE)

    private fun renderMetaRow(
        rating: Float?,
        year: String?,
        genres: List<String>,
        area: String?
    ) {
        binding.metaRow.removeAllViews()
        if (rating != null) {
            addMetaChip("★ %.1f".format(rating), true)
        }
        year?.takeIf { it.isNotBlank() }?.let { addMetaChip(it, false) }
        genres.take(2).forEach { addMetaChip(it, false) }
        area?.takeIf { it.isNotBlank() }?.let { addMetaChip(it, false) }
    }

    private fun addMetaChip(text: String, highlight: Boolean) {
        val chip = TextView(this).apply {
            this.text = text
            setTextColor(getColor(if (highlight) R.color.primary_yellow else R.color.text_secondary))
            textSize = 12f
            setBackgroundResource(R.drawable.bg_tag)
            setPadding(16, 8, 16, 8)
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = 12
        binding.metaRow.addView(chip, params)
    }

    private fun renderInfoGrid(state: DetailUiState.Success) {
        val media = state.media
        binding.infoGrid.removeAllViews()
        val rows = listOf(
            "导演" to media.director,
            "类型" to media.genres.joinToString(" / ").ifBlank { null },
            "上映日期" to media.releaseDate,
            "评分" to media.rating?.let { "%.1f".format(it) }
        )
        rows.filter { !it.second.isNullOrBlank() }.forEach { (label, value) ->
            addInfoCell(label, value.orEmpty())
        }
    }

    private fun renderActors(actors: String?) {
        val text = actors?.trim().orEmpty()
        binding.actorsSection.isVisible = text.isNotBlank()
        if (text.isBlank()) return

        binding.actorsText.text = text
        binding.actorsText.maxLines = Int.MAX_VALUE
        binding.actorsText.post {
            val needsExpand = binding.actorsText.lineCount > ACTORS_COLLAPSED_LINES
            binding.actorsToggle.isVisible = needsExpand
            applyActorsExpandedState()
        }
    }

    private fun applyActorsExpandedState() {
        binding.actorsText.maxLines = if (actorsExpanded) Int.MAX_VALUE else ACTORS_COLLAPSED_LINES
        binding.actorsToggle.text = getString(
            if (actorsExpanded) R.string.collapse else R.string.more
        )
    }

    private fun addInfoCell(label: String, value: String) {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val titleView = TextView(context).apply {
                text = label
                setTextColor(getColor(R.color.text_secondary))
                textSize = 12f
            }
            val valueView = TextView(context).apply {
                text = value
                setTextColor(getColor(R.color.text_primary))
                textSize = 14f
            }
            addView(titleView)
            addView(valueView)
        }
        val params = GridLayout.LayoutParams().apply {
            width = 0
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(0, 0, 32, 24)
        }
        binding.infoGrid.addView(cell, params)
    }

    private fun updatePlotToggleVisibility() {
        binding.plotText.maxLines = Int.MAX_VALUE
        binding.plotText.post {
            val needsExpand = binding.plotText.lineCount > 3
            binding.plotToggle.isVisible = needsExpand
            binding.plotText.maxLines = if (plotExpanded) Int.MAX_VALUE else 3
            binding.plotToggle.text = getString(
                if (plotExpanded) R.string.collapse else R.string.expand_all
            )
        }
    }

    private fun togglePlot() {
        plotExpanded = !plotExpanded
        binding.plotText.maxLines = if (plotExpanded) Int.MAX_VALUE else 3
        binding.plotToggle.text = getString(if (plotExpanded) R.string.collapse else R.string.expand_all)
    }

    private fun toggleActors() {
        actorsExpanded = !actorsExpanded
        applyActorsExpandedState()
    }

    private fun playResume() {
        lifecycleScope.launch {
            val state = viewModel.uiState.value as? DetailUiState.Success
            val showEpisodes = currentEpisodes.isNotEmpty()
            if (showEpisodes) {
                val episodeId = selectedEpisodeId ?: currentEpisodes.firstOrNull()?.id
                val episode = episodeId?.let { id ->
                    currentEpisodes.find { it.id == id }
                        ?: state?.episodes?.find { it.id == id }
                }
                if (episode != null) {
                    playEpisode(episode)
                    return@launch
                }
            }
            val info = viewModel.getResumePlaybackInfo() ?: return@launch
            if (info.videoUrl.isBlank()) return@launch
            PlayerLauncher.createIntent(
                context = this@DetailActivity,
                videoUrl = info.videoUrl,
                title = info.title,
                episodeTitle = info.episodeTitle,
                mediaId = info.mediaId,
                episodeId = info.episodeId,
                startPosition = info.startPosition,
                posterUrl = info.posterUrl
            ).also { playerLauncher.launch(it) }
        }
    }

    private fun playEpisode(episode: EpisodeItem) {
        lifecycleScope.launch {
            val info = viewModel.getEpisodePlaybackInfo(episode.id) ?: return@launch
            if (info.videoUrl.isBlank()) return@launch
            val mediaTitle = (viewModel.uiState.value as? DetailUiState.Success)?.media?.title
            val displayEpisodeTitle = EpisodeLabelFormatter.build(
                episode.episodeNumber,
                episode.title,
                mediaTitle
            )
            PlayerLauncher.createIntent(
                context = this@DetailActivity,
                videoUrl = info.videoUrl,
                title = info.title,
                episodeTitle = displayEpisodeTitle,
                mediaId = info.mediaId,
                episodeId = info.episodeId,
                startPosition = info.startPosition,
                posterUrl = info.posterUrl
            ).also { playerLauncher.launch(it) }
        }
    }

    private fun showOfflineCachePicker() {
        lifecycleScope.launch {
            val state = viewModel.uiState.value as? DetailUiState.Success ?: return@launch
            val sources = state.playSources
            if (sources.isEmpty()) {
                Toast.makeText(this@DetailActivity, R.string.offline_download_no_url, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val openEpisodePicker: (Int) -> Unit = { sourceIndex ->
                lifecycleScope.launch episodePicker@{
                    val episodes = viewModel.getEpisodesForCacheSource(sourceIndex)
                    if (episodes.isEmpty()) {
                        Toast.makeText(
                            this@DetailActivity,
                            R.string.offline_download_no_url,
                            Toast.LENGTH_SHORT
                        ).show()
                        return@episodePicker
                    }

                    val downloads = offlineDownloadService.getAllDownloads().first()
                    val statusByEpisode = downloads
                        .filter { it.mediaId == state.media.id && it.episodeId != null }
                        .associate { it.episodeId!! to it.status }

                    // 当前线路仅 1 条地址：选完线路直接缓存，不再弹第二层
                    if (episodes.size == 1) {
                        enqueueEpisodesToOfflineCache(episodes, statusByEpisode)
                        return@episodePicker
                    }

                    val sourceName = sources.getOrNull(sourceIndex).orEmpty()
                    val isMovieOptions = state.media.type == MediaType.MOVIE
                    val title = when {
                        sourceName.isBlank() && isMovieOptions ->
                            getString(R.string.offline_cache_option_picker_title)
                        sourceName.isBlank() ->
                            getString(R.string.offline_cache_picker_title)
                        isMovieOptions ->
                            getString(R.string.offline_cache_option_picker_title_with_source, sourceName)
                        else ->
                            getString(R.string.offline_cache_episode_picker_title_with_source, sourceName)
                    }

                    OfflineCachePickerDialog.show(
                        context = this@DetailActivity,
                        episodes = episodes,
                        episodeStatuses = statusByEpisode,
                        title = title
                    ) { selectedEpisodes ->
                        lifecycleScope.launch {
                            enqueueEpisodesToOfflineCache(selectedEpisodes, statusByEpisode)
                        }
                    }
                }
            }

            if (sources.size == 1) {
                openEpisodePicker(0)
                return@launch
            }

            OfflineCacheSourcePickerDialog.show(
                context = this@DetailActivity,
                sources = sources,
                selectedIndex = state.selectedPlaySourceIndex,
                onConfirm = openEpisodePicker
            )
        }
    }

    private suspend fun enqueueEpisodesToOfflineCache(
        episodes: List<EpisodeItem>,
        statusByEpisode: Map<String, String>
    ) {
        var addedCount = 0
        for (episode in episodes) {
            val existingStatus = statusByEpisode[episode.id]
            if (existingStatus != null && existingStatus != OfflineDownloadStatus.FAILED) {
                continue
            }
            val info = viewModel.buildEpisodePlaybackInfo(episode) ?: continue
            if (info.videoUrl.isBlank()) continue
            offlineDownloadService.enqueueDownload(
                mediaId = info.mediaId,
                episodeId = info.episodeId,
                title = info.title,
                episodeTitle = info.episodeTitle,
                posterUrl = info.posterUrl,
                videoUrl = info.videoUrl
            )
            addedCount++
        }
        when {
            addedCount > 0 -> Toast.makeText(
                this,
                getString(R.string.offline_download_batch_added, addedCount),
                Toast.LENGTH_SHORT
            ).show()
            else -> Toast.makeText(
                this,
                R.string.offline_download_batch_none,
                Toast.LENGTH_SHORT
            ).show()
        }
        refreshCacheButtonState()
    }

    private fun addCurrentEpisodeToOfflineCache() {
        lifecycleScope.launch {
            val state = viewModel.uiState.value as? DetailUiState.Success ?: return@launch
            val target = resolveDownloadTarget(state) ?: run {
                Toast.makeText(this@DetailActivity, R.string.offline_download_no_url, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val existing = offlineDownloadService.findDownload(target.mediaId, target.episodeId)
            if (existing != null && existing.status != OfflineDownloadStatus.FAILED) {
                Toast.makeText(this@DetailActivity, R.string.offline_download_exists, Toast.LENGTH_SHORT).show()
                refreshCacheButtonState()
                return@launch
            }
            offlineDownloadService.enqueueDownload(
                mediaId = target.mediaId,
                episodeId = target.episodeId,
                title = target.title,
                episodeTitle = target.episodeTitle,
                posterUrl = target.posterUrl,
                videoUrl = target.videoUrl
            )
            Toast.makeText(this@DetailActivity, R.string.offline_download_added, Toast.LENGTH_SHORT).show()
            refreshCacheButtonState()
        }
    }

    private suspend fun resolveDownloadTarget(state: DetailUiState.Success): DownloadTarget? {
        val showEpisodes = state.episodes.isNotEmpty()
        return if (showEpisodes) {
            val episodeId = selectedEpisodeId ?: currentEpisodes.firstOrNull()?.id ?: return null
            val info = viewModel.getEpisodePlaybackInfo(episodeId) ?: return null
            if (info.videoUrl.isBlank()) return null
            DownloadTarget(
                mediaId = info.mediaId,
                episodeId = info.episodeId,
                title = info.title,
                episodeTitle = info.episodeTitle,
                posterUrl = info.posterUrl,
                videoUrl = info.videoUrl
            )
        } else {
            val info = viewModel.getResumePlaybackInfo() ?: return null
            if (info.videoUrl.isBlank()) return null
            DownloadTarget(
                mediaId = info.mediaId,
                episodeId = info.episodeId,
                title = info.title,
                episodeTitle = info.episodeTitle ?: getString(R.string.offline_movie_label),
                posterUrl = info.posterUrl,
                videoUrl = info.videoUrl
            )
        }
    }

    private fun refreshCacheButtonState() {
        lifecycleScope.launch {
            val state = viewModel.uiState.value as? DetailUiState.Success ?: return@launch
            val showEpisodes = currentEpisodes.isNotEmpty()
            if (showEpisodes) {
                binding.cacheLabel.text = getString(R.string.add_cache)
                return@launch
            }
            val target = resolveDownloadTarget(state)
            if (target == null) {
                binding.cacheLabel.text = getString(R.string.add_cache)
                return@launch
            }
            val existing = offlineDownloadService.findDownload(target.mediaId, target.episodeId)
            binding.cacheLabel.text = when (existing?.status) {
                OfflineDownloadStatus.COMPLETED -> getString(R.string.offline_cached)
                OfflineDownloadStatus.DOWNLOADING,
                OfflineDownloadStatus.PENDING -> getString(R.string.offline_downloading)
                OfflineDownloadStatus.PAUSED -> getString(R.string.offline_paused)
                OfflineDownloadStatus.FAILED -> getString(R.string.offline_retry)
                else -> getString(R.string.add_cache)
            }
        }
    }

    private data class DownloadTarget(
        val mediaId: String,
        val episodeId: String?,
        val title: String,
        val episodeTitle: String?,
        val posterUrl: String?,
        val videoUrl: String
    )

    companion object {
        private const val EXTRA_MEDIA_ID = "extra_media_id"
        private const val EPISODE_COLUMNS = 3
        private const val EPISODE_ROWS = 5
        private const val EPISODES_PER_PAGE = EPISODE_COLUMNS * EPISODE_ROWS
        private const val ACTORS_COLLAPSED_LINES = 2

        fun intent(context: Context, mediaId: String): Intent {
            return Intent(context, DetailActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_ID, mediaId)
            }
        }
    }
}

private class EpisodeAdapter(
    private val onClick: (EpisodeItem) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.Holder>() {

    private val episodes = mutableListOf<EpisodeItem>()
    private var highlightId: String? = null
    private var recyclerView: RecyclerView? = null
    private var clearHighlightRunnable: Runnable? = null

    fun submitEpisodes(items: List<EpisodeItem>, highlightId: String?) {
        episodes.clear()
        episodes.addAll(items)
        this.highlightId = highlightId
        notifyDataSetChanged()
    }

    fun flashSelection(episodeId: String) {
        highlightId = episodeId
        notifyDataSetChanged()
        val host = recyclerView ?: return
        clearHighlightRunnable?.let { host.removeCallbacks(it) }
        val clear = Runnable {
            if (highlightId == episodeId) {
                highlightId = null
                notifyDataSetChanged()
            }
        }
        clearHighlightRunnable = clear
        host.postDelayed(clear, FLASH_DURATION_MS)
    }

    override fun onAttachedToRecyclerView(host: RecyclerView) {
        super.onAttachedToRecyclerView(host)
        recyclerView = host
    }

    override fun onDetachedFromRecyclerView(host: RecyclerView) {
        if (recyclerView === host) {
            clearHighlightRunnable?.let { host.removeCallbacks(it) }
            clearHighlightRunnable = null
            recyclerView = null
        }
        super.onDetachedFromRecyclerView(host)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode, parent, false) as TextView
        return Holder(view)
    }

    override fun getItemCount(): Int = episodes.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val episode = episodes[position]
        holder.bind(episode, episode.id == highlightId, onClick)
    }

    class Holder(private val view: TextView) : RecyclerView.ViewHolder(view) {
        fun bind(episode: EpisodeItem, highlighted: Boolean, onClick: (EpisodeItem) -> Unit) {
            view.text = EpisodeLabelFormatter.cellLabel(episode.episodeNumber, episode.title)
            if (highlighted) {
                view.setBackgroundResource(R.drawable.bg_episode_cell_selected)
                view.setTextColor(view.context.getColor(R.color.background_dark))
            } else {
                view.setBackgroundResource(R.drawable.ripple_episode_cell)
                view.setTextColor(view.context.getColor(R.color.text_primary))
            }
            view.setOnClickListener { onClick(episode) }
        }
    }

    companion object {
        private const val FLASH_DURATION_MS = 180L
    }
}

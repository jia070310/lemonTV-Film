package com.lemon.yingshi.mobile.ui

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.DialogOfflineCachePickerBinding
import com.lemon.yingshi.tv.data.local.database.entity.OfflineDownloadStatus
import com.lemon.yingshi.tv.ui.screens.detail.EpisodeItem
import com.lemon.yingshi.tv.utils.EpisodeLabelFormatter
import kotlin.math.max
import kotlin.math.min

object OfflineCachePickerDialog {

    private const val COLUMNS = 3
    private const val PER_PAGE = 18

    data class EpisodeCacheState(
        val episode: EpisodeItem,
        val downloadStatus: String?
    ) {
        val isSelectable: Boolean
            get() = downloadStatus == null || downloadStatus == OfflineDownloadStatus.FAILED
    }

    fun show(
        context: Context,
        episodes: List<EpisodeItem>,
        episodeStatuses: Map<String, String?>,
        title: String? = null,
        onConfirm: (List<EpisodeItem>) -> Unit
    ): Dialog {
        val binding = DialogOfflineCachePickerBinding.inflate(LayoutInflater.from(context))
        binding.titleText.text = title ?: context.getString(R.string.offline_cache_picker_title)
        val states = episodes.sortedBy { it.episodeNumber }.map { episode ->
            EpisodeCacheState(episode, episodeStatuses[episode.id])
        }
        val selectableIds = states.filter { it.isSelectable }.map { it.episode.id }.toMutableSet()
        val selectedIds = mutableSetOf<String>()
        val totalPages = max(1, (states.size + PER_PAGE - 1) / PER_PAGE)
        var currentPage = 0

        lateinit var adapter: CacheEpisodeAdapter

        fun updateSelectedCount() {
            binding.selectedCountText.text = context.getString(
                R.string.offline_cache_selected_count,
                selectedIds.size
            )
            binding.confirmButton.isEnabled = selectedIds.isNotEmpty()
            binding.confirmButton.alpha = if (selectedIds.isNotEmpty()) 1f else 0.45f
            val allSelected = selectableIds.isNotEmpty() && selectedIds.containsAll(selectableIds)
            binding.selectAllButton.text = context.getString(
                if (allSelected) R.string.offline_cache_deselect_all else R.string.offline_cache_select_all
            )
        }

        fun renderPage() {
            currentPage = currentPage.coerceIn(0, totalPages - 1)
            val pageItems = states.drop(currentPage * PER_PAGE).take(PER_PAGE)
            adapter.submit(pageItems, selectedIds)
            val showPagination = totalPages > 1
            binding.pageIndicator.isVisible = showPagination
            binding.prevPageButton.isVisible = showPagination
            binding.nextPageButton.isVisible = showPagination
            if (showPagination) {
                binding.pageIndicator.text = context.getString(
                    R.string.page_indicator,
                    currentPage + 1,
                    totalPages
                )
                binding.prevPageButton.alpha = if (currentPage > 0) 1f else 0.4f
                binding.nextPageButton.alpha = if (currentPage < totalPages - 1) 1f else 0.4f
            }
            updateSelectedCount()
        }

        adapter = CacheEpisodeAdapter(
            onToggle = { episodeId ->
                if (episodeId !in selectableIds) return@CacheEpisodeAdapter
                if (selectedIds.contains(episodeId)) {
                    selectedIds.remove(episodeId)
                } else {
                    selectedIds.add(episodeId)
                }
                renderPage()
            }
        )
        binding.episodeGrid.layoutManager = GridLayoutManager(context, COLUMNS)
        binding.episodeGrid.adapter = adapter

        binding.selectAllButton.setOnClickListener {
            if (selectableIds.isEmpty()) return@setOnClickListener
            if (selectedIds.containsAll(selectableIds)) {
                selectedIds.clear()
            } else {
                selectedIds.clear()
                selectedIds.addAll(selectableIds)
            }
            renderPage()
        }
        binding.prevPageButton.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                renderPage()
            }
        }
        binding.nextPageButton.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                renderPage()
            }
        }

        val dialog = Dialog(context)
        dialog.setContentView(binding.root)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val widthPx = min(dp(context, 340), (screenWidth * 0.9f).toInt())
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            attributes = attributes?.apply { dimAmount = 0.5f }
        }

        binding.cancelButton.setOnClickListener { dialog.dismiss() }
        binding.confirmButton.setOnClickListener {
            val picked = states
                .filter { it.episode.id in selectedIds }
                .map { it.episode }
            onConfirm(picked)
            dialog.dismiss()
        }

        renderPage()
        dialog.show()
        return dialog
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

private class CacheEpisodeAdapter(
    private val onToggle: (String) -> Unit
) : RecyclerView.Adapter<CacheEpisodeAdapter.Holder>() {

    private val items = mutableListOf<OfflineCachePickerDialog.EpisodeCacheState>()
    private var selectedIds: Set<String> = emptySet()

    fun submit(data: List<OfflineCachePickerDialog.EpisodeCacheState>, selected: Set<String>) {
        items.clear()
        items.addAll(data)
        selectedIds = selected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_player_episode_cell, parent, false) as TextView
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], selectedIds.contains(items[position].episode.id), onToggle)
    }

    class Holder(private val view: TextView) : RecyclerView.ViewHolder(view) {
        fun bind(
            state: OfflineCachePickerDialog.EpisodeCacheState,
            selected: Boolean,
            onToggle: (String) -> Unit
        ) {
            val episode = state.episode
            view.text = EpisodeLabelFormatter.cellLabel(episode.episodeNumber, episode.title)
            when {
                !state.isSelectable -> {
                    view.alpha = 0.35f
                    view.setBackgroundResource(R.drawable.bg_filter_tag)
                    view.setTextColor(view.context.getColor(R.color.text_muted))
                }
                selected -> {
                    view.alpha = 1f
                    view.setBackgroundColor(view.context.getColor(R.color.primary_yellow))
                    view.setTextColor(view.context.getColor(R.color.background_dark))
                }
                else -> {
                    view.alpha = 1f
                    view.setBackgroundResource(R.drawable.bg_filter_tag)
                    view.setTextColor(view.context.getColor(R.color.text_primary))
                }
            }
            view.setOnClickListener {
                if (state.isSelectable) {
                    onToggle(episode.id)
                }
            }
        }
    }
}

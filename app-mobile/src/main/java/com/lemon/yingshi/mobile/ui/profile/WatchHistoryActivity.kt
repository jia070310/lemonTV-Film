package com.lemon.yingshi.mobile.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityWatchHistoryBinding
import com.lemon.yingshi.mobile.ui.home.PosterGridLayout
import com.lemon.yingshi.mobile.ui.player.PlayerLauncher
import com.lemon.yingshi.tv.domain.service.WatchHistoryItem
import com.lemon.yingshi.mobile.util.setBackNavigation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WatchHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWatchHistoryBinding
    private val viewModel: WatchHistoryViewModel by viewModels()
    private lateinit var adapter: WatchHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWatchHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setBackNavigation { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_watch_history)
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_clear_history) {
                confirmClearHistory()
                true
            } else {
                false
            }
        }

        adapter = WatchHistoryAdapter { item ->
            lifecycleScope.launch {
                val info = viewModel.getPlaybackInfo(item) ?: run {
                    Toast.makeText(this@WatchHistoryActivity, R.string.developing, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val videoPath = info.videoPath
                if (videoPath.isNullOrBlank()) {
                    Toast.makeText(this@WatchHistoryActivity, R.string.developing, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                PlayerLauncher.launch(
                    context = this@WatchHistoryActivity,
                    videoUrl = videoPath,
                    title = info.title,
                    episodeTitle = info.episodeTitle,
                    mediaId = info.mediaId,
                    episodeId = info.episodeId,
                    startPosition = info.startPosition,
                    posterUrl = item.posterUrl ?: item.backdropUrl
                )
            }
        }

        val spacing = resources.getDimensionPixelSize(R.dimen.card_spacing)
        binding.historyRecycler.apply {
            PosterGridLayout.setup(this, spacing)
            adapter = this@WatchHistoryActivity.adapter
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.historyItems.collect { items ->
                    adapter.submit(items)
                    binding.emptyText.isVisible = items.isEmpty()
                    binding.historyRecycler.isVisible = items.isNotEmpty()
                    binding.toolbar.title = getString(R.string.watch_history_title) +
                        if (items.isNotEmpty()) " (${items.size})" else ""
                }
            }
        }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setMessage(R.string.watch_history_clear_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.clearAllHistory()
            }
            .show()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, WatchHistoryActivity::class.java)
    }
}

private class WatchHistoryAdapter(
    private val onClick: (WatchHistoryItem) -> Unit
) : RecyclerView.Adapter<WatchHistoryAdapter.Holder>() {

    private val items = mutableListOf<WatchHistoryItem>()

    fun submit(data: List<WatchHistoryItem>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vod_grid, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], onClick)
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val poster = itemView.findViewById<ImageView>(R.id.poster_image)
        private val title = itemView.findViewById<TextView>(R.id.title_text)
        private val tags = itemView.findViewById<LinearLayout>(R.id.tags_container)
        private val ratingBadge = itemView.findViewById<View>(R.id.rating_badge)

        fun bind(item: WatchHistoryItem, onClick: (WatchHistoryItem) -> Unit) {
            title.text = item.title
            ratingBadge.isVisible = false

            tags.removeAllViews()
            val context = itemView.context
            val inflater = LayoutInflater.from(context)
            fun addTag(text: String) {
                val tagView = inflater.inflate(R.layout.view_vod_tag, tags, false) as TextView
                tagView.text = text
                tags.addView(tagView)
            }
            addTag(context.getString(R.string.progress_percent, item.progressPercent))
            item.episodeTitle?.takeIf { it.isNotBlank() }?.let { addTag(it) }
            tags.isVisible = tags.childCount > 0

            poster.load(item.coverImageModel()) {
                crossfade(true)
                placeholder(R.drawable.bg_poster_card)
                error(R.drawable.bg_poster_card)
            }

            itemView.setOnClickListener { onClick(item) }
        }
    }
}

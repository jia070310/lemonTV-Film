package com.lemon.yingshi.mobile.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityOfflineDownloadsBinding
import com.lemon.yingshi.mobile.ui.player.PlayerLauncher
import com.lemon.yingshi.tv.data.local.database.entity.OfflineDownloadStatus
import com.lemon.yingshi.tv.domain.service.OfflineDownloadItem
import com.lemon.yingshi.mobile.util.setBackNavigation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OfflineDownloadsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfflineDownloadsBinding
    private val viewModel: OfflineDownloadsViewModel by viewModels()
    private lateinit var adapter: OfflineDownloadAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfflineDownloadsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setBackNavigation { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_offline_downloads)
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_clear_offline) {
                confirmClearAll()
                true
            } else {
                false
            }
        }

        adapter = OfflineDownloadAdapter(
            onClick = { item -> playOffline(item) },
            onToggle = { item -> toggleDownload(item) },
            onDelete = { item -> confirmDelete(item) }
        )
        binding.offlineRecycler.apply {
            layoutManager = LinearLayoutManager(this@OfflineDownloadsActivity)
            adapter = this@OfflineDownloadsActivity.adapter
        }

        binding.pauseAllButton.setOnClickListener { viewModel.pauseAll() }
        binding.resumeAllButton.setOnClickListener { viewModel.resumeAll() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.downloads.collect { items ->
                    adapter.submit(items)
                    binding.emptyContainer.isVisible = items.isEmpty()
                    binding.offlineRecycler.isVisible = items.isNotEmpty()
                    binding.batchActions.isVisible = items.isNotEmpty()
                    binding.toolbar.title = getString(R.string.offline_downloads_title) +
                        if (items.isNotEmpty()) " (${items.size})" else ""
                    updateBatchButtons(items)
                }
            }
        }
    }

    private fun updateBatchButtons(items: List<OfflineDownloadItem>) {
        val hasPausable = items.any { it.canPause }
        val hasResumable = items.any { it.canResume }
        binding.pauseAllButton.isEnabled = hasPausable
        binding.pauseAllButton.alpha = if (hasPausable) 1f else 0.45f
        binding.resumeAllButton.isEnabled = hasResumable
        binding.resumeAllButton.alpha = if (hasResumable) 1f else 0.45f
    }

    private fun toggleDownload(item: OfflineDownloadItem) {
        when {
            item.canPause -> viewModel.pauseDownload(item.id)
            item.canResume -> viewModel.resumeDownload(item.id)
        }
    }

    private fun playOffline(item: OfflineDownloadItem) {
        if (!item.isCompleted) {
            Toast.makeText(this, statusLabel(item), Toast.LENGTH_SHORT).show()
            return
        }
        val localUrl = item.localPlaybackUrl
        if (localUrl.isNullOrBlank()) {
            Toast.makeText(this, R.string.offline_download_play_failed, Toast.LENGTH_SHORT).show()
            return
        }
        PlayerLauncher.launch(
            context = this,
            videoUrl = localUrl,
            title = item.title,
            episodeTitle = item.episodeTitle,
            mediaId = item.mediaId,
            episodeId = item.episodeId
        )
    }

    private fun confirmDelete(item: OfflineDownloadItem) {
        AlertDialog.Builder(this)
            .setMessage(R.string.offline_download_delete_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.deleteDownload(item.id)
            }
            .show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setMessage(R.string.offline_downloads_clear_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.clearAll()
            }
            .show()
    }

    private fun statusLabel(item: OfflineDownloadItem): String = when (item.status) {
        OfflineDownloadStatus.PENDING -> getString(R.string.offline_status_pending)
        OfflineDownloadStatus.DOWNLOADING -> getString(R.string.offline_status_downloading, item.progress)
        OfflineDownloadStatus.PAUSED -> getString(R.string.offline_status_paused, item.progress)
        OfflineDownloadStatus.FAILED -> getString(R.string.offline_status_failed)
        else -> getString(R.string.offline_status_completed)
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, OfflineDownloadsActivity::class.java)
    }
}

private class OfflineDownloadAdapter(
    private val onClick: (OfflineDownloadItem) -> Unit,
    private val onToggle: (OfflineDownloadItem) -> Unit,
    private val onDelete: (OfflineDownloadItem) -> Unit
) : RecyclerView.Adapter<OfflineDownloadAdapter.Holder>() {

    private val items = mutableListOf<OfflineDownloadItem>()

    fun submit(data: List<OfflineDownloadItem>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_offline_download, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], onClick, onToggle, onDelete)
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val poster = itemView.findViewById<ImageView>(R.id.poster_image)
        private val title = itemView.findViewById<TextView>(R.id.title_text)
        private val episode = itemView.findViewById<TextView>(R.id.episode_text)
        private val status = itemView.findViewById<TextView>(R.id.status_text)
        private val progressBar = itemView.findViewById<ProgressBar>(R.id.progress_bar)
        private val toggleButton = itemView.findViewById<ImageButton>(R.id.toggle_button)
        private val deleteButton = itemView.findViewById<ImageButton>(R.id.delete_button)

        fun bind(
            item: OfflineDownloadItem,
            onClick: (OfflineDownloadItem) -> Unit,
            onToggle: (OfflineDownloadItem) -> Unit,
            onDelete: (OfflineDownloadItem) -> Unit
        ) {
            title.text = item.title
            episode.text = item.episodeTitle ?: itemView.context.getString(R.string.offline_movie_label)
            episode.isVisible = true
            poster.load(item.posterUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_poster_card)
                error(R.drawable.bg_poster_card)
            }

            when (item.status) {
                OfflineDownloadStatus.DOWNLOADING -> {
                    status.text = itemView.context.getString(
                        R.string.offline_status_downloading,
                        item.progress
                    )
                    progressBar.isVisible = true
                    progressBar.progress = item.progress
                }
                OfflineDownloadStatus.PENDING -> {
                    status.text = itemView.context.getString(R.string.offline_status_pending)
                    progressBar.isVisible = true
                    progressBar.progress = item.progress
                }
                OfflineDownloadStatus.PAUSED -> {
                    status.text = itemView.context.getString(
                        R.string.offline_status_paused,
                        item.progress
                    )
                    progressBar.isVisible = true
                    progressBar.progress = item.progress
                }
                OfflineDownloadStatus.COMPLETED -> {
                    status.text = itemView.context.getString(R.string.offline_status_completed)
                    progressBar.isVisible = false
                }
                OfflineDownloadStatus.FAILED -> {
                    status.text = item.errorMessage?.takeIf { it.isNotBlank() }
                        ?: itemView.context.getString(R.string.offline_status_failed)
                    progressBar.isVisible = false
                }
                else -> {
                    status.text = item.status
                    progressBar.isVisible = false
                }
            }

            when {
                item.canPause -> {
                    toggleButton.isVisible = true
                    toggleButton.setImageResource(R.drawable.ic_player_pause)
                    toggleButton.contentDescription =
                        itemView.context.getString(R.string.offline_pause)
                }
                item.canResume -> {
                    toggleButton.isVisible = true
                    toggleButton.setImageResource(R.drawable.ic_player_play)
                    toggleButton.contentDescription =
                        itemView.context.getString(R.string.offline_resume)
                }
                else -> {
                    toggleButton.isVisible = false
                }
            }

            itemView.setOnClickListener { onClick(item) }
            toggleButton.setOnClickListener { onToggle(item) }
            deleteButton.setOnClickListener { onDelete(item) }
        }
    }
}

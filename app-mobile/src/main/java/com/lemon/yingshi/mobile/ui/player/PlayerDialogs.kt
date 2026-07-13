package com.lemon.yingshi.mobile.ui.player

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.DialogPlayerEpisodeListBinding
import com.lemon.yingshi.mobile.databinding.DialogPlayerSkipBinding
import com.lemon.yingshi.mobile.databinding.DialogPlayerSpeedBinding
import com.lemon.yingshi.tv.data.local.database.entity.SkipConfigEntity
import com.lemon.yingshi.tv.ui.player.PlayerViewModel
import kotlin.math.max
import kotlin.math.min

object PlayerDialogs {

    private fun Dialog.stylePlayerPanel(
        context: Context,
        widthPx: Int,
        gravity: Int = Gravity.CENTER
    ) {
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(gravity)
            attributes = attributes?.apply { dimAmount = 0.45f }
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    fun showSpeedDialog(
        context: Context,
        speeds: List<Float>,
        currentSpeed: Float,
        onSelect: (Float) -> Unit
    ): Dialog {
        val binding = DialogPlayerSpeedBinding.inflate(LayoutInflater.from(context))
        speeds.forEach { speed ->
            val selected = speed == currentSpeed
            val button = TextView(context).apply {
                text = "${speed}X"
                textSize = 13f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
                setBackgroundResource(R.drawable.ripple_player_dialog_speed_item)
                isSelected = selected
                if (selected) {
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(context.getColor(R.color.primary_yellow))
                } else {
                    setTextColor(context.getColor(R.color.text_primary))
                }
                setOnClickListener { onSelect(speed) }
            }
            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(context, 2)
            binding.speedOptions.addView(button, params)
        }

        val dialog = Dialog(context)
        dialog.setContentView(binding.root)
        dialog.stylePlayerPanel(
            context = context,
            widthPx = dp(context, 100),
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        )
        dialog.show()
        return dialog
    }

    fun showEpisodeListDialog(
        context: Context,
        episodes: List<PlayerViewModel.EpisodeListItem>,
        currentEpisodeId: String?,
        onSelect: (PlayerViewModel.EpisodeListItem) -> Unit
    ): Dialog {
        val binding = DialogPlayerEpisodeListBinding.inflate(LayoutInflater.from(context))
        val sorted = episodes.sortedBy { it.episodeNumber }
        val columns = 4
        val perPage = 20
        val totalPages = max(1, (sorted.size + perPage - 1) / perPage)
        var currentPage = currentEpisodeId?.let { id ->
            val index = sorted.indexOfFirst { it.id == id }.coerceAtLeast(0)
            index / perPage
        } ?: 0

        lateinit var adapter: EpisodePickAdapter
        fun renderPage() {
            currentPage = currentPage.coerceIn(0, totalPages - 1)
            val pageItems = sorted.drop(currentPage * perPage).take(perPage)
            adapter.submit(pageItems, currentEpisodeId)
            val showPagination = totalPages > 1
            binding.episodePagination.isVisible = showPagination
            binding.episodePageIndicator.isVisible = showPagination
            if (showPagination) {
                binding.episodePageIndicator.text = "${currentPage + 1}/$totalPages"
                binding.episodePrevPage.alpha = if (currentPage > 0) 1f else 0.4f
                binding.episodeNextPage.alpha = if (currentPage < totalPages - 1) 1f else 0.4f
            }
        }

        adapter = EpisodePickAdapter { episode ->
            onSelect(episode)
        }
        binding.episodeGrid.layoutManager = GridLayoutManager(context, columns)
        binding.episodeGrid.adapter = adapter
        binding.episodePrevPage.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                renderPage()
            }
        }
        binding.episodeNextPage.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                renderPage()
            }
        }
        renderPage()

        val screenWidth = context.resources.displayMetrics.widthPixels
        val widthPx = min(dp(context, 280), (screenWidth * 0.72f).toInt())

        val dialog = Dialog(context)
        dialog.setContentView(binding.root)
        dialog.stylePlayerPanel(
            context = context,
            widthPx = widthPx,
            gravity = Gravity.BOTTOM or Gravity.END
        )
        dialog.show()
        return dialog
    }

    fun showSkipConfigDialog(
        context: Context,
        config: SkipConfigEntity,
        scopeTitle: String,
        durationMs: Long,
        onSave: (SkipConfigEntity) -> Unit,
        onReset: () -> Unit,
        onSetIntroEnd: () -> Long,
        onSetOutroStart: () -> Long
    ): Dialog {
        val binding = DialogPlayerSkipBinding.inflate(LayoutInflater.from(context))
        val maxDuration = if (durationMs > 0) min(durationMs / 2, 600_000L) else 600_000L
        var introMs = config.introDuration
        var outroMs = config.outroDuration
        var skipIntro = config.skipIntroEnabled
        var skipOutro = config.skipOutroEnabled

        binding.skipScopeTitle.text = scopeTitle
        binding.skipIntroSwitch.isChecked = skipIntro
        binding.skipOutroSwitch.isChecked = skipOutro

        fun updateLabels() {
            binding.introValue.text = PlayerTimeFormatter.format(introMs)
            binding.outroValue.text = PlayerTimeFormatter.format(outroMs)
        }

        fun setupSeek(seekBar: SeekBar, getValue: () -> Long, setValue: (Long) -> Unit) {
            seekBar.max = maxDuration.toInt().coerceAtLeast(1)
            seekBar.progress = getValue().toInt().coerceIn(0, seekBar.max)
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) setValue(progress.toLong())
                    updateLabels()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }

        setupSeek(binding.introSeek, { introMs }) { introMs = it }
        setupSeek(binding.outroSeek, { outroMs }) { outroMs = it }
        updateLabels()

        binding.skipIntroSwitch.setOnCheckedChangeListener { _, checked -> skipIntro = checked }
        binding.skipOutroSwitch.setOnCheckedChangeListener { _, checked -> skipOutro = checked }

        val dialog = Dialog(context)
        dialog.setContentView(binding.root)

        binding.setIntroEnd.setOnClickListener {
            introMs = onSetIntroEnd().coerceAtMost(maxDuration)
            binding.introSeek.progress = introMs.toInt().coerceAtMost(binding.introSeek.max)
            updateLabels()
        }
        binding.setOutroStart.setOnClickListener {
            outroMs = onSetOutroStart().coerceAtMost(maxDuration)
            binding.outroSeek.progress = outroMs.toInt().coerceAtMost(binding.outroSeek.max)
            updateLabels()
        }
        binding.skipReset.setOnClickListener {
            onReset()
            dialog.dismiss()
        }
        binding.skipSave.setOnClickListener {
            onSave(
                config.copy(
                    introDuration = introMs,
                    outroDuration = outroMs,
                    skipIntroEnabled = skipIntro,
                    skipOutroEnabled = skipOutro
                )
            )
            dialog.dismiss()
        }

        val screenWidth = context.resources.displayMetrics.widthPixels
        val widthPx = min(dp(context, 280), (screenWidth * 0.82f).toInt())
        dialog.stylePlayerPanel(context, widthPx, Gravity.CENTER)
        dialog.show()
        return dialog
    }

    fun showResumeDialog(
        context: Context,
        startPositionMs: Long,
        onContinue: () -> Unit,
        onRestart: () -> Unit
    ): Dialog {
        return MaterialAlertDialogBuilder(context)
            .setMessage(context.getString(R.string.player_resume_prompt, PlayerTimeFormatter.format(startPositionMs)))
            .setPositiveButton(R.string.player_continue) { _, _ -> onContinue() }
            .setNegativeButton(R.string.player_restart) { _, _ -> onRestart() }
            .setNeutralButton(R.string.player_close) { dialog, _ ->
                dialog.dismiss()
                onContinue()
            }
            .create()
            .also { it.show() }
    }

    fun showErrorDialog(
        context: Context,
        message: String,
        onRetry: () -> Unit
    ): Dialog {
        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.player_error_title)
            .setMessage(message)
            .setPositiveButton(R.string.player_retry) { _, _ -> onRetry() }
            .setNegativeButton(R.string.player_close, null)
            .create()
            .also { it.show() }
    }
}

private class EpisodePickAdapter(
    private val onClick: (PlayerViewModel.EpisodeListItem) -> Unit
) : RecyclerView.Adapter<EpisodePickAdapter.Holder>() {

    private val items = mutableListOf<PlayerViewModel.EpisodeListItem>()
    private var selectedId: String? = null

    fun submit(data: List<PlayerViewModel.EpisodeListItem>, selected: String?) {
        items.clear()
        items.addAll(data)
        selectedId = selected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_player_episode_cell, parent, false) as TextView
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], items[position].id == selectedId, onClick)
    }

    class Holder(private val view: TextView) : RecyclerView.ViewHolder(view) {
        fun bind(
            episode: PlayerViewModel.EpisodeListItem,
            selected: Boolean,
            onClick: (PlayerViewModel.EpisodeListItem) -> Unit
        ) {
            view.text = "%02d".format(episode.episodeNumber)
            if (selected) {
                view.setBackgroundColor(view.context.getColor(R.color.primary_yellow))
                view.setTextColor(view.context.getColor(R.color.background_dark))
            } else {
                view.setBackgroundResource(R.drawable.bg_filter_tag)
                view.setTextColor(view.context.getColor(R.color.text_primary))
            }
            view.setOnClickListener { onClick(episode) }
        }
    }
}

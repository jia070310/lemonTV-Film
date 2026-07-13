package com.lemon.yingshi.mobile.ui.home

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lemon.yingshi.mobile.R

object HorizontalPosterRowLayout {

    fun setup(recyclerView: RecyclerView, adapter: VodGridAdapter) {
        val spacingPx = recyclerView.resources.getDimensionPixelSize(R.dimen.card_spacing)
        val visibleColumns = PosterGridLayout.spanCount(recyclerView.context)
        adapter.configureHorizontalRow(visibleColumns, spacingPx)

        recyclerView.layoutManager = LinearLayoutManager(
            recyclerView.context,
            LinearLayoutManager.HORIZONTAL,
            false
        )
        while (recyclerView.itemDecorationCount > 0) {
            recyclerView.removeItemDecorationAt(0)
        }
        recyclerView.addItemDecoration(HorizontalSpacingItemDecoration(spacingPx))
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = null
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.clipToPadding = false

        fun refreshItemWidths() {
            if (recyclerView.width <= 0) return
            adapter.updateHorizontalWidth(recyclerView.width)
        }

        recyclerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refreshItemWidths()
        }
        recyclerView.post { refreshItemWidths() }
    }
}

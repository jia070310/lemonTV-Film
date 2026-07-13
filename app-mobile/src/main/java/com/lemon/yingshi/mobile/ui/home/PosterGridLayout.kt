package com.lemon.yingshi.mobile.ui.home

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lemon.yingshi.mobile.R

object PosterGridLayout {

    fun spanCount(context: Context): Int =
        context.resources.getInteger(R.integer.poster_grid_span_count)

    fun setup(recyclerView: RecyclerView, spacingPx: Int) {
        val spanCount = spanCount(recyclerView.context)
        recyclerView.layoutManager = GridLayoutManager(recyclerView.context, spanCount)
        if (recyclerView.itemDecorationCount == 0) {
            recyclerView.addItemDecoration(
                GridSpacingItemDecoration(spanCount = spanCount, spacingPx = spacingPx)
            )
        }
    }
}

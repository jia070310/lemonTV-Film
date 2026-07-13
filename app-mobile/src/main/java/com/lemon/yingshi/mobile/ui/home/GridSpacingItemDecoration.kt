package com.lemon.yingshi.mobile.ui.home

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class GridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacingPx: Int
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val column = position % spanCount
        outRect.left = column * spacingPx / spanCount
        outRect.right = spacingPx - (column + 1) * spacingPx / spanCount
        if (position >= spanCount) {
            outRect.top = spacingPx
        }
    }
}

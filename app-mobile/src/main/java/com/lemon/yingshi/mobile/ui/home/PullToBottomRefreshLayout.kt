package com.lemon.yingshi.mobile.ui.home

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.lemon.yingshi.mobile.R
import kotlin.math.max

/**
 * 首页下拉刷新：需在列表顶部按住并向下拖动，手指接近屏幕底部后松手才触发刷新。
 */
class PullToBottomRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val bottomReachSlopPx = dp(28)
    private val maxContentOffsetPx = dp(72)

    private var initialRawY = 0f
    private var lastRawY = 0f
    private var isDragging = false
    private var reachedBottom = false

    var canRefresh: Boolean = true
    var isRefreshing: Boolean = false
        set(value) {
            field = value
            refreshIndicator.isIndeterminate = value
            refreshIndicator.isVisible = value
            if (value) {
                resetPull(animated = false)
            }
        }

    private var refreshListener: (() -> Unit)? = null
    private var scrollChild: View? = null

    private val refreshIndicator = CircularProgressIndicator(context).apply {
        isIndeterminate = false
        max = 100
        progress = 0
        isVisible = false
        indicatorSize = dp(32)
        setIndicatorColor(ContextCompat.getColor(context, R.color.primary_yellow))
        trackColor = ContextCompat.getColor(context, R.color.surface_elevated)
    }

    fun setOnRefreshListener(listener: () -> Unit) {
        refreshListener = listener
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        scrollChild = if (childCount > 0) getChildAt(0) else null
        addView(
            refreshIndicator,
            LayoutParams(dp(32), dp(32)).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                topMargin = dp(12)
            }
        )
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!canRefresh || isRefreshing) return false
        val child = scrollChild ?: return false
        if (child.canScrollVertically(-1)) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialRawY = event.rawY
                lastRawY = initialRawY
                isDragging = false
                reachedBottom = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - initialRawY
                if (dy > touchSlop) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!canRefresh || isRefreshing) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                lastRawY = event.rawY
                val bottomY = bottomTriggerY()
                val travel = max(bottomY - initialRawY, touchSlop.toFloat())
                val progress = ((event.rawY - initialRawY) / travel).coerceIn(0f, 1f)
                reachedBottom = event.rawY >= bottomY
                updatePullUi(progress, reachedBottom)

                scrollChild?.translationY =
                    ((event.rawY - initialRawY) * 0.25f).coerceAtMost(maxContentOffsetPx.toFloat())
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val shouldRefresh = reachedBottom
                resetPull(animated = true)
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (shouldRefresh) {
                    refreshListener?.invoke()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updatePullUi(progress: Float, ready: Boolean) {
        refreshIndicator.isVisible = true
        refreshIndicator.isIndeterminate = false
        refreshIndicator.progress = (progress * 100).toInt()
        refreshIndicator.alpha = if (ready) 1f else 0.55f + progress * 0.45f
    }

    private fun resetPull(animated: Boolean) {
        if (animated) {
            scrollChild?.animate()?.translationY(0f)?.setDuration(180)?.start()
        } else {
            scrollChild?.translationY = 0f
        }
        if (!isRefreshing) {
            refreshIndicator.isVisible = false
            refreshIndicator.progress = 0
        }
        reachedBottom = false
        isDragging = false
    }

    private fun bottomTriggerY(): Float {
        return resources.displayMetrics.heightPixels - bottomReachSlopPx.toFloat()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

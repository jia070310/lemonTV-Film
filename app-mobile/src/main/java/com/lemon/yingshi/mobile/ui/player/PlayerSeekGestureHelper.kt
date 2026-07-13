package com.lemon.yingshi.mobile.ui.player

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * 播放页水平滑动手势：左右拖动预览进度，松手后跳转；快速滑动则按步进秒数快进/快退。
 */
class PlayerSeekGestureHelper(
    context: Context,
    private val targetView: View,
    private val isTouchOnControls: (MotionEvent) -> Boolean,
    private val getDurationMs: () -> Long,
    private val getCurrentPositionMs: () -> Long,
    private val getSeekStepMs: () -> Long,
    private val onSeekPreview: (previewPositionMs: Long, deltaMs: Long) -> Unit,
    private val onSeekCommit: (positionMs: Long) -> Unit,
    private val onSeekCancel: () -> Unit,
    private val onSingleTap: () -> Unit,
    private val onInteraction: () -> Unit,
) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity

    private var downX = 0f
    private var downY = 0f
    private var startPositionMs = 0L
    private var seeking = false
    private var screenWidthPx = context.resources.displayMetrics.widthPixels

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isTouchOnControls(e)) return false
                onSingleTap()
                onInteraction()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null || seeking || isTouchOnControls(e1)) return false
                if (abs(velocityX) < minFlingVelocity) return false
                if (abs(velocityX) <= abs(velocityY) * 1.2f) return false

                val step = getSeekStepMs()
                val delta = if (velocityX > 0) step else -step
                val duration = getDurationMs()
                if (duration <= 0) return false

                val target = (getCurrentPositionMs() + delta).coerceIn(0L, duration)
                onSeekPreview(target, delta)
                onSeekCommit(target)
                onInteraction()
                return true
            }
        }
    )

    private val touchListener = View.OnTouchListener { view, event ->
        screenWidthPx = view.width.coerceAtLeast(1)

        if (event.action == MotionEvent.ACTION_DOWN && isTouchOnControls(event)) {
            seeking = false
            return@OnTouchListener false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                startPositionMs = getCurrentPositionMs()
                seeking = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!seeking) {
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.2f) {
                        seeking = true
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        onInteraction()
                    } else {
                        return@OnTouchListener gestureDetector.onTouchEvent(event)
                    }
                }
                val preview = previewPositionForDelta(dx)
                onSeekPreview(preview, preview - startPositionMs)
                return@OnTouchListener true
            }

            MotionEvent.ACTION_UP -> {
                if (seeking) {
                    val preview = previewPositionForDelta(event.x - downX)
                    onSeekCommit(preview)
                    seeking = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    return@OnTouchListener true
                }
                return@OnTouchListener gestureDetector.onTouchEvent(event)
            }

            MotionEvent.ACTION_CANCEL -> {
                if (seeking) {
                    onSeekCancel()
                    seeking = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    return@OnTouchListener true
                }
            }
        }

        gestureDetector.onTouchEvent(event)
    }

    fun attach() {
        targetView.isClickable = true
        targetView.setOnTouchListener(touchListener)
    }

    fun detach() {
        targetView.setOnTouchListener(null)
    }

    /** 滑满屏约等于跳转 2 分钟，上限不超过总时长 */
    private fun previewPositionForDelta(deltaX: Float): Long {
        val duration = getDurationMs()
        if (duration <= 0) return 0L
        val maxDelta = minOf(120_000L, duration)
        val deltaMs = (deltaX / screenWidthPx * maxDelta).toLong()
        return (startPositionMs + deltaMs).coerceIn(0L, duration)
    }
}

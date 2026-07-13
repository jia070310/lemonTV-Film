package com.lemon.yingshi.mobile.util

import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.content.ContextCompat

object TouchFeedback {

    fun applyBorderlessRipple(view: View) {
        if (view.foreground == null) {
            val typedValue = TypedValue()
            view.context.theme.resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless,
                typedValue,
                true
            )
            view.foreground = ContextCompat.getDrawable(view.context, typedValue.resourceId)
        }
        view.isClickable = true
        view.isFocusable = true
    }

    fun applyRipple(view: View) {
        if (view.foreground == null) {
            val typedValue = TypedValue()
            view.context.theme.resolveAttribute(
                android.R.attr.selectableItemBackground,
                typedValue,
                true
            )
            view.foreground = ContextCompat.getDrawable(view.context, typedValue.resourceId)
        }
        view.isClickable = true
        view.isFocusable = true
    }

    fun View.setClickWithFeedback(onClick: () -> Unit) {
        applyBorderlessRipple(this)
        setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
    }
}

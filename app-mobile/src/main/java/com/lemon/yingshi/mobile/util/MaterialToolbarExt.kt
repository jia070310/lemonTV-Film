package com.lemon.yingshi.mobile.util

import android.view.HapticFeedbackConstants
import com.google.android.material.appbar.MaterialToolbar

fun MaterialToolbar.setBackNavigation(onBack: () -> Unit) {
    setNavigationOnClickListener { view ->
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onBack()
    }
}

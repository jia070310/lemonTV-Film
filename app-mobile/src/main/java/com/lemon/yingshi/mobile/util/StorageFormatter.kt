package com.lemon.yingshi.mobile.util

import kotlin.math.ln
import kotlin.math.pow

object StorageFormatter {

    fun format(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return if (digitGroups == 0) {
            "${bytes} B"
        } else {
            "%.1f %s".format(value, units[digitGroups])
        }
    }
}

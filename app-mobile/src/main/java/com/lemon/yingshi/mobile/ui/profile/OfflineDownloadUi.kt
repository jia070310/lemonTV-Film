package com.lemon.yingshi.mobile.ui.profile

import android.content.Context
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.tv.domain.service.OfflineDownloadSummary
import com.lemon.yingshi.tv.util.StorageFormatter

object OfflineDownloadUi {

    fun formatCacheDesc(context: Context, summary: OfflineDownloadSummary): String {
        return if (summary.totalCount > 0) {
            context.getString(
                R.string.offline_cache_desc,
                summary.downloadingCount,
                summary.completedCount,
                StorageFormatter.format(summary.totalStorageBytes)
            )
        } else {
            context.getString(R.string.offline_cache_desc_empty)
        }
    }

    fun formatTotalSize(context: Context, summary: OfflineDownloadSummary): String {
        return context.getString(
            R.string.offline_downloads_total_size,
            StorageFormatter.format(summary.totalStorageBytes),
            summary.totalCount
        )
    }

    fun formatItemSize(bytes: Long): String = StorageFormatter.format(bytes)
}

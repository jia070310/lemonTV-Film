package com.lemon.yingshi.mobile.ui.profile

import android.content.Context
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.tv.domain.service.OfflineDownloadSummary

object OfflineDownloadUi {

    fun formatCacheDesc(context: Context, summary: OfflineDownloadSummary): String {
        return if (summary.totalCount > 0) {
            context.getString(
                R.string.offline_cache_desc,
                summary.downloadingCount,
                summary.completedCount
            )
        } else {
            context.getString(R.string.offline_cache_desc_empty)
        }
    }
}

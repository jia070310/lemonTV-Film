package com.lemon.yingshi.tv.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 下载完成广播接收器（已废弃，使用OkHttp直接下载）
 */
class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 不再使用系统DownloadManager，此接收器保留但为空实现
    }
}
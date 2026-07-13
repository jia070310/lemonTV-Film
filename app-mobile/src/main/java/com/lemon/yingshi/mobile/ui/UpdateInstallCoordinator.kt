package com.lemon.yingshi.mobile.ui

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.tv.domain.service.ApkInstallResult
import com.lemon.yingshi.tv.domain.service.DownloadService
import java.io.File

object UpdateInstallCoordinator {

    private var pendingApkFile: File? = null

    fun rememberPendingInstall(apkFile: File) {
        pendingApkFile = apkFile
    }

    fun clearPendingInstall() {
        pendingApkFile = null
    }

    fun install(activity: AppCompatActivity, apkFile: File) {
        rememberPendingInstall(apkFile)
        handleInstall(activity, apkFile)
    }

    fun retryPendingInstall(activity: AppCompatActivity) {
        val apkFile = pendingApkFile ?: return
        handleInstall(activity, apkFile)
    }

    private fun handleInstall(context: Context, apkFile: File) {
        when (DownloadService(context).installApk(apkFile, context)) {
            ApkInstallResult.Started -> clearPendingInstall()
            ApkInstallResult.NeedInstallPermission -> {
                Toast.makeText(
                    context,
                    R.string.about_install_permission,
                    Toast.LENGTH_LONG
                ).show()
            }
            ApkInstallResult.Failed -> {
                Toast.makeText(
                    context,
                    R.string.about_install_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

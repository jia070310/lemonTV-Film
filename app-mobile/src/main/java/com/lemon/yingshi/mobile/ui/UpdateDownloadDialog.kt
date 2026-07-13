package com.lemon.yingshi.mobile.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lemon.yingshi.mobile.R
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object UpdateDownloadDialog {

    private var dialog: Dialog? = null

    fun show(activity: AppCompatActivity, progress: Int = 0) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (dialog?.isShowing != true) {
            dialog = Dialog(activity, R.style.Theme_LomenMobile_DownloadProgress).apply {
                setContentView(R.layout.dialog_download_progress)
                setCancelable(false)
                setCanceledOnTouchOutside(false)
                window?.apply {
                    setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                }
            }
            dialog?.show()
        }
        updateProgress(activity, progress)
    }

    fun updateProgress(activity: AppCompatActivity, progress: Int) {
        dialog?.findViewById<TextView>(R.id.download_progress_text)?.text =
            activity.getString(R.string.about_downloading_progress, progress)
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    fun observe(
        activity: AppCompatActivity,
        isDownloading: StateFlow<Boolean>,
        downloadProgress: StateFlow<Int>,
        downloadFailed: SharedFlow<Unit>,
        installApk: SharedFlow<java.io.File>? = null
    ) {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    isDownloading.collect { downloading ->
                        if (downloading) {
                            show(activity, downloadProgress.value)
                        } else {
                            dismiss()
                        }
                    }
                }
                launch {
                    downloadProgress.collect { progress ->
                        if (isDownloading.value) {
                            updateProgress(activity, progress)
                        }
                    }
                }
                launch {
                    downloadFailed.collect {
                        Toast.makeText(
                            activity,
                            R.string.about_download_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                installApk?.let { flow ->
                    launch {
                        flow.collect { apkFile ->
                            UpdateInstallCoordinator.install(activity, apkFile)
                        }
                    }
                }
            }
        }
    }
}

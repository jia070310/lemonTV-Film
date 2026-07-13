package com.lemon.yingshi.mobile.ui.profile

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.DialogVersionUpdateBinding
import com.lemon.yingshi.tv.domain.model.VersionInfo

object AboutDialogs {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun styleDialogWindow(dialog: Dialog, widthPx: Int) {
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            attributes = attributes?.apply { dimAmount = 0.45f }
        }
    }

    fun showVersionUpdateDialog(
        context: Context,
        versionInfo: VersionInfo,
        onUpdate: () -> Unit
    ): Dialog {
        val binding = DialogVersionUpdateBinding.inflate(LayoutInflater.from(context))
        binding.updateTitle.text = context.getString(
            R.string.about_update_title,
            versionInfo.versionName
        )
        binding.releaseNotes.text = versionInfo.releaseNotes.ifBlank {
            context.getString(R.string.about_update_no_notes)
        }

        val dialog = Dialog(context)
        dialog.setContentView(binding.root)
        styleDialogWindow(
            dialog,
            minOf(dp(context, 300), (context.resources.displayMetrics.widthPixels * 0.82f).toInt())
        )

        binding.cancelButton.setOnClickListener { dialog.dismiss() }
        binding.updateButton.setOnClickListener {
            onUpdate()
            dialog.dismiss()
        }
        dialog.show()
        return dialog
    }
}

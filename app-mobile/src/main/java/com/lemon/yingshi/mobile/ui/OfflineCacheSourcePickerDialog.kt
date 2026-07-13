package com.lemon.yingshi.mobile.ui

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.DialogOfflineCacheSourcePickerBinding
import kotlin.math.min

object OfflineCacheSourcePickerDialog {

    fun show(
        context: Context,
        sources: List<String>,
        selectedIndex: Int,
        onConfirm: (Int) -> Unit
    ): Dialog {
        val binding = DialogOfflineCacheSourcePickerBinding.inflate(LayoutInflater.from(context))
        var currentIndex = selectedIndex.coerceIn(0, (sources.size - 1).coerceAtLeast(0))
        val chipViews = mutableListOf<TextView>()

        fun updateSelection() {
            chipViews.forEachIndexed { index, chip ->
                chip.isSelected = index == currentIndex
            }
        }

        val inflater = LayoutInflater.from(context)
        val marginEnd = context.resources.getDimensionPixelSize(R.dimen.card_spacing) / 2
        sources.forEachIndexed { index, name ->
            val chip = inflater.inflate(
                R.layout.view_filter_chip,
                binding.sourceContainer,
                false
            ) as TextView
            chip.text = name
            chip.setOnClickListener {
                currentIndex = index
                updateSelection()
            }
            binding.sourceContainer.addView(
                chip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { this.marginEnd = marginEnd }
            )
            chipViews += chip
        }
        updateSelection()

        val dialog = Dialog(context)
        dialog.setContentView(binding.root)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val widthPx = min(dp(context, 340), (screenWidth * 0.9f).toInt())
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            attributes = attributes?.apply { dimAmount = 0.5f }
        }

        binding.cancelButton.setOnClickListener { dialog.dismiss() }
        binding.confirmButton.setOnClickListener {
            onConfirm(currentIndex)
            dialog.dismiss()
        }

        dialog.show()
        return dialog
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

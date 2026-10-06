package com.lemon.yingshi.mobile.ui.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.DialogCategorySortBinding
import com.lemon.yingshi.mobile.databinding.DialogPrivacyKeywordsBinding
import com.lemon.yingshi.mobile.databinding.DialogSettingsConfirmBinding
import com.lemon.yingshi.mobile.databinding.DialogSettingsMenuBinding
import com.lemon.yingshi.mobile.databinding.ItemCategorySortBinding
import com.lemon.yingshi.tv.data.remote.model.MacCmsServerEntry

object SettingsDialogs {

    data class CategorySortItem(
        val key: String,
        val name: String,
        var visible: Boolean,
        /** 二级所属一级 key；隐私隐藏联动用 */
        val parentKey: String? = null,
        /** 一级下全部二级 key；隐私隐藏联动用 */
        val childKeys: List<String> = emptyList()
    )

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun styleDialogWindow(dialog: Dialog, widthPx: Int, gravity: Int = Gravity.CENTER) {
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(gravity)
            attributes = attributes?.apply { dimAmount = 0.45f }
        }
    }

    fun showConfirmDialog(
        context: Context,
        message: String,
        onConfirm: () -> Unit
    ): Dialog {
        val binding = DialogSettingsConfirmBinding.inflate(LayoutInflater.from(context))
        binding.messageText.text = message
        val dialog = Dialog(context)
        dialog.setContentView(binding.root)
        styleDialogWindow(dialog, (context.resources.displayMetrics.widthPixels * 0.86f).toInt())
        binding.cancelButton.setOnClickListener { dialog.dismiss() }
        binding.confirmButton.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }
        dialog.show()
        return dialog
    }

    fun showSeekDurationMenu(
        context: Context,
        options: List<Int>,
        currentSeconds: Int,
        onSelect: (Int) -> Unit
    ): Dialog {
        val binding = DialogSettingsMenuBinding.inflate(LayoutInflater.from(context))
        binding.dialogTitle.setText(R.string.settings_seek_duration)
        val dialog = Dialog(context)
        dialog.setContentView(binding.root)

        options.forEach { seconds ->
            val selected = seconds == currentSeconds
            val item = TextView(context).apply {
                text = context.getString(R.string.settings_seek_seconds_format, seconds)
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
                setBackgroundResource(R.drawable.ripple_settings_dialog_item)
                if (selected) {
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(context.getColor(R.color.primary_yellow))
                } else {
                    setTextColor(context.getColor(R.color.text_primary))
                }
                setOnClickListener {
                    onSelect(seconds)
                    dialog.dismiss()
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(context, 6)
            binding.optionsContainer.addView(item, params)
        }

        styleDialogWindow(dialog, dp(context, 220), Gravity.CENTER)
        dialog.show()
        return dialog
    }

    fun showCategorySortDialog(
        context: Context,
        items: List<CategorySortItem>,
        isLoading: Boolean,
        errorMessage: String?,
        onSave: (List<CategorySortItem>) -> Unit
    ): Dialog {
        return showCategoryListDialog(
            context = context,
            titleRes = R.string.settings_home_sort,
            items = items,
            isLoading = isLoading,
            errorMessage = errorMessage,
            showReorder = true,
            onSave = onSave
        )
    }

    fun showPrivacyHideDialog(
        context: Context,
        items: List<CategorySortItem>,
        isLoading: Boolean,
        errorMessage: String?,
        servers: List<MacCmsServerEntry> = emptyList(),
        selectedServerUrl: String = "",
        onServerSelected: ((String) -> Unit)? = null,
        onSave: (List<CategorySortItem>) -> Unit
    ): Dialog {
        return showCategoryListDialog(
            context = context,
            titleRes = R.string.settings_privacy_hide,
            items = items,
            isLoading = isLoading,
            errorMessage = errorMessage,
            showReorder = false,
            servers = servers,
            selectedServerUrl = selectedServerUrl,
            onServerSelected = onServerSelected,
            onSave = onSave
        )
    }

    fun showPrivacyKeywordsDialog(
        context: Context,
        currentKeywords: String,
        servers: List<MacCmsServerEntry>,
        selectedServerUrl: String,
        onServerSelected: (serverUrl: String, applyKeywords: (String) -> Unit) -> Unit,
        onSave: (serverUrl: String, keywords: String) -> Unit
    ): Dialog {
        val binding = DialogPrivacyKeywordsBinding.inflate(LayoutInflater.from(context))
        binding.keywordsInput.setText(currentKeywords)
        var currentUrl = selectedServerUrl
        bindServerSpinner(binding.serverSpinner, servers, selectedServerUrl) { url ->
            currentUrl = url
            onServerSelected(url) { binding.keywordsInput.setText(it) }
        }
        val dialog = Dialog(context)
        dialog.setContentView(binding.root)
        styleDialogWindow(
            dialog,
            minOf(dp(context, 300), (context.resources.displayMetrics.widthPixels * 0.86f).toInt())
        )
        binding.cancelButton.setOnClickListener { dialog.dismiss() }
        binding.saveButton.setOnClickListener {
            onSave(currentUrl, binding.keywordsInput.text?.toString().orEmpty())
            dialog.dismiss()
        }
        dialog.show()
        return dialog
    }

    private fun showCategoryListDialog(
        context: Context,
        titleRes: Int,
        items: List<CategorySortItem>,
        isLoading: Boolean,
        errorMessage: String?,
        showReorder: Boolean,
        servers: List<MacCmsServerEntry> = emptyList(),
        selectedServerUrl: String = "",
        onServerSelected: ((String) -> Unit)? = null,
        onSave: (List<CategorySortItem>) -> Unit
    ): Dialog {
        val binding = DialogCategorySortBinding.inflate(LayoutInflater.from(context))
        val titleView = binding.root.getChildAt(0) as? TextView
        titleView?.setText(titleRes)
        val showServerPicker = servers.isNotEmpty() && onServerSelected != null
        binding.serverPickerLabel.isVisible = showServerPicker
        binding.serverSpinner.isVisible = showServerPicker
        if (showServerPicker) {
            bindServerSpinner(binding.serverSpinner, servers, selectedServerUrl) { url ->
                onServerSelected?.invoke(url)
            }
        }

        val editable = items.map { it.copy() }.toMutableList()
        val adapter = CategorySortAdapter(editable, showReorder)

        val rowHeight = dp(context, 34)
        val maxListHeight = dp(context, 180)
        val listHeight = when {
            isLoading || !errorMessage.isNullOrBlank() -> 0
            editable.isEmpty() -> dp(context, 32)
            else -> minOf(editable.size * rowHeight, maxListHeight)
        }
        binding.sortListContainer.layoutParams = binding.sortListContainer.layoutParams.apply {
            height = listHeight
        }
        binding.sortListContainer.isVisible = listHeight > 0

        binding.sortRecycler.layoutManager = LinearLayoutManager(context)
        binding.sortRecycler.adapter = adapter
        binding.loadingIndicator.isVisible = isLoading
        binding.sortRecycler.isVisible = !isLoading && errorMessage.isNullOrBlank()
        binding.errorText.isVisible = !errorMessage.isNullOrBlank()
        binding.errorText.text = errorMessage
        binding.saveButton.isEnabled = !isLoading && errorMessage.isNullOrBlank()
        binding.saveButton.alpha = if (binding.saveButton.isEnabled) 1f else 0.45f

        val dialog = Dialog(context)
        dialog.setContentView(binding.root)
        styleDialogWindow(
            dialog,
            minOf(dp(context, 300), (context.resources.displayMetrics.widthPixels * 0.72f).toInt())
        )

        binding.cancelButton.setOnClickListener { dialog.dismiss() }
        binding.saveButton.setOnClickListener {
            if (!binding.saveButton.isEnabled) return@setOnClickListener
            onSave(editable.map { it.copy() })
            dialog.dismiss()
        }
        dialog.show()
        return dialog
    }

    private fun bindServerSpinner(
        spinner: Spinner,
        servers: List<MacCmsServerEntry>,
        selectedServerUrl: String,
        onServerSelected: (String) -> Unit
    ) {
        val adapter = ServerSpinnerAdapter(spinner.context, servers)
        spinner.adapter = adapter
        spinner.dropDownWidth = ViewGroup.LayoutParams.MATCH_PARENT
        val index = servers.indexOfFirst { it.url == selectedServerUrl }.coerceAtLeast(0)
        adapter.selectedUrl = servers.getOrNull(index)?.url.orEmpty()
        spinner.setSelection(index, false)
        var lastUrl = servers.getOrNull(index)?.url.orEmpty()
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val url = servers.getOrNull(position)?.url ?: return
                adapter.selectedUrl = url
                if (url == lastUrl) return
                lastUrl = url
                onServerSelected(url)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private class ServerSpinnerAdapter(
        context: Context,
        private val servers: List<MacCmsServerEntry>
    ) : ArrayAdapter<MacCmsServerEntry>(context, 0, servers) {

        var selectedUrl: String = servers.firstOrNull()?.url.orEmpty()

        override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_privacy_server_closed, parent, false)
            bindRow(view, servers[position], collapsed = true, selected = false)
            return view
        }

        override fun getDropDownView(
            position: Int,
            convertView: android.view.View?,
            parent: ViewGroup
        ): android.view.View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_privacy_server_dropdown, parent, false)
            val entry = servers[position]
            val selected = entry.url == selectedUrl
            bindRow(view, entry, collapsed = false, selected = selected)
            view.isActivated = selected
            return view
        }

        private fun bindRow(
            view: android.view.View,
            entry: MacCmsServerEntry,
            collapsed: Boolean,
            selected: Boolean
        ) {
            val title = view.findViewById<TextView>(R.id.server_title)
            val subtitle = view.findViewById<TextView>(R.id.server_subtitle)
            val hasName = entry.name.isNotBlank()
            title.text = if (hasName) entry.name else entry.url
            title.setTextColor(
                context.getColor(if (selected && !collapsed) R.color.primary_yellow else R.color.text_primary)
            )
            if (collapsed || !hasName) {
                subtitle.isVisible = false
            } else {
                subtitle.isVisible = true
                subtitle.text = entry.url
            }
        }
    }

    private class CategorySortAdapter(
        private val items: MutableList<CategorySortItem>,
        private val showReorder: Boolean
    ) : RecyclerView.Adapter<CategorySortAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemCategorySortBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return Holder(binding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position], position)
        }

        inner class Holder(
            private val binding: ItemCategorySortBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: CategorySortItem, position: Int) {
                binding.titleText.text = item.name
                binding.visibleSwitch.setOnCheckedChangeListener(null)
                binding.visibleSwitch.isChecked = item.visible
                binding.visibleSwitch.setOnCheckedChangeListener { _, checked ->
                    if (showReorder) {
                        item.visible = checked
                    } else {
                        applyPrivacyVisibilityCascade(item, checked)
                    }
                }
                binding.moveUpButton.isVisible = showReorder
                binding.moveDownButton.isVisible = showReorder
                if (!showReorder) return

                binding.moveUpButton.isEnabled = position > 0
                binding.moveUpButton.alpha = if (position > 0) 1f else 0.35f
                binding.moveDownButton.isEnabled = position < items.lastIndex
                binding.moveDownButton.alpha = if (position < items.lastIndex) 1f else 0.35f
                binding.moveUpButton.setOnClickListener {
                    val index = bindingAdapterPosition
                    if (index <= 0) return@setOnClickListener
                    swap(index, index - 1)
                }
                binding.moveDownButton.setOnClickListener {
                    val index = bindingAdapterPosition
                    if (index < 0 || index >= items.lastIndex) return@setOnClickListener
                    swap(index, index + 1)
                }
            }

            /** 隐私隐藏：关闭一级时二级一并关闭；打开一级时二级一并打开 */
            private fun applyPrivacyVisibilityCascade(item: CategorySortItem, visible: Boolean) {
                item.visible = visible
                val changed = mutableSetOf(item.key)
                if (item.childKeys.isNotEmpty()) {
                    items.forEach { other ->
                        if (other.key in item.childKeys) {
                            other.visible = visible
                            changed.add(other.key)
                        }
                    }
                }
                if (visible && item.parentKey != null) {
                    items.forEach { other ->
                        if (other.key == item.parentKey) {
                            other.visible = true
                            changed.add(other.key)
                        }
                    }
                }
                items.forEachIndexed { index, row ->
                    if (row.key in changed) {
                        notifyItemChanged(index)
                    }
                }
            }

            private fun swap(from: Int, to: Int) {
                val temp = items[from]
                items[from] = items[to]
                items[to] = temp
                notifyItemMoved(from, to)
                notifyItemChanged(from)
                notifyItemChanged(to)
            }
        }
    }
}

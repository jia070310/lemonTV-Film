package com.lemon.yingshi.mobile.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityFavoritesBinding
import com.lemon.yingshi.mobile.ui.DetailActivity
import com.lemon.yingshi.mobile.ui.home.PosterGridLayout
import com.lemon.yingshi.tv.domain.service.FavoriteItem
import com.lemon.yingshi.mobile.util.setBackNavigation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FavoritesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritesBinding
    private val viewModel: FavoritesViewModel by viewModels()
    private lateinit var adapter: FavoritePosterAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setBackNavigation { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_favorites)
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_clear_favorites) {
                confirmClearFavorites()
                true
            } else {
                false
            }
        }

        adapter = FavoritePosterAdapter { item ->
            startActivity(DetailActivity.intent(this, item.mediaId))
        }

        val spacing = resources.getDimensionPixelSize(R.dimen.card_spacing)
        binding.favoritesRecycler.apply {
            PosterGridLayout.setup(this, spacing)
            adapter = this@FavoritesActivity.adapter
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.favorites.collect { items ->
                    adapter.submit(items)
                    binding.emptyContainer.isVisible = items.isEmpty()
                    binding.favoritesRecycler.isVisible = items.isNotEmpty()
                    binding.toolbar.title = getString(R.string.favorites_title) +
                        if (items.isNotEmpty()) " (${items.size})" else ""
                }
            }
        }
    }

    private fun confirmClearFavorites() {
        AlertDialog.Builder(this)
            .setMessage(R.string.favorites_clear_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.clearAllFavorites()
            }
            .show()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, FavoritesActivity::class.java)
    }
}

private class FavoritePosterAdapter(
    private val onClick: (FavoriteItem) -> Unit
) : RecyclerView.Adapter<FavoritePosterAdapter.Holder>() {

    private val items = mutableListOf<FavoriteItem>()

    fun submit(data: List<FavoriteItem>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vod_grid, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], onClick)
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val poster = itemView.findViewById<ImageView>(R.id.poster_image)
        private val title = itemView.findViewById<TextView>(R.id.title_text)
        private val tags = itemView.findViewById<View>(R.id.tags_container)
        private val ratingBadge = itemView.findViewById<View>(R.id.rating_badge)
        private val ratingText = itemView.findViewById<TextView>(R.id.rating_text)

        fun bind(item: FavoriteItem, onClick: (FavoriteItem) -> Unit) {
            title.text = item.title
            tags.isVisible = false
            val rating = item.rating
            ratingBadge.isVisible = rating != null
            ratingText.text = rating?.toString().orEmpty()
            poster.load(item.coverImageUrl()) {
                crossfade(true)
                placeholder(R.drawable.bg_poster_card)
                error(R.drawable.bg_poster_card)
            }
            itemView.setOnClickListener { onClick(item) }
        }
    }
}

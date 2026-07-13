package com.lemon.yingshi.mobile.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem

object VodUiBinder {

    fun bindPoster(
        imageView: ImageView,
        vod: MacCmsVodItem
    ) {
        val imageUrl = vod.vodPic?.takeIf { it.isNotBlank() }
            ?: vod.vodPicThumb?.takeIf { it.isNotBlank() }
        if (imageUrl.isNullOrBlank()) {
            imageView.setImageResource(R.drawable.bg_poster_card)
            return
        }
        imageView.load(imageUrl) {
            crossfade(true)
            placeholder(R.drawable.bg_poster_card)
            error(R.drawable.bg_poster_card)
        }
    }

    fun bindRating(badge: View, ratingText: TextView, score: String?) {
        val value = score?.toFloatOrNull()
        if (value == null) {
            badge.visibility = View.GONE
            return
        }
        badge.visibility = View.VISIBLE
        ratingText.text = "%.1f".format(value)
    }

    fun bindTags(container: LinearLayout, vod: MacCmsVodItem) {
        container.removeAllViews()
        val tags = buildList {
            vod.vodYear?.takeIf { it.isNotBlank() }?.let { add(it) }
            val genre = vod.vodClass?.split(Regex("[,，|]"))?.firstOrNull()?.trim()
                ?: vod.typeName?.takeIf { it.isNotBlank() }
            if (!genre.isNullOrBlank()) add(genre)
        }
        val inflater = LayoutInflater.from(container.context)
        tags.forEach { tag ->
            val tagView = inflater.inflate(R.layout.view_vod_tag, container, false) as TextView
            tagView.text = tag
            container.addView(tagView)
        }
    }

    fun openDetail(context: Context, vod: MacCmsVodItem, cache: (MacCmsVodItem) -> Unit) {
        cache(vod)
        val mediaId = com.lemon.yingshi.tv.data.remote.model.MacCmsIds.encode(vod.vodId)
        context.startActivity(DetailActivity.intent(context, mediaId))
    }
}

package com.lemon.yingshi.mobile.ui.profile

import android.widget.ImageView
import coil.load
import coil.transform.CircleCropTransformation
import com.lemon.yingshi.tv.domain.service.UserAvatarStore

object UserAvatarUi {

    fun bind(
        imageView: ImageView,
        avatarStore: UserAvatarStore,
        defaultRes: Int,
        revision: Long = 0L
    ) {
        val avatarFile = avatarStore.getAvatarFile()
        if (avatarFile != null) {
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.load(avatarFile) {
                crossfade(true)
                transformations(CircleCropTransformation())
                memoryCacheKey("user_avatar_$revision")
                diskCacheKey("user_avatar_$revision")
            }
        } else {
            bindDefault(imageView, defaultRes)
        }
    }

    fun bindDefault(imageView: ImageView, defaultRes: Int) {
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageView.setImageResource(defaultRes)
    }
}

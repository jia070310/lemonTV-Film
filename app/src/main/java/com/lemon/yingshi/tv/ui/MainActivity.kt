package com.lemon.yingshi.tv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.lemon.yingshi.tv.R
import com.lemon.yingshi.tv.ui.navigation.LomenTVNavigation
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.LomenTVTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 勿在 super.onCreate 前 setTheme(主主题)：会覆盖清单里的 Theme.LomenTV.Splash，导致启动画面全屏底色不显示。
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge experience
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            LomenTVTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundDark)
                ) {
                    LomenTVNavigation()
                }
            }
        }

        // 首帧之后把窗口底色改为与界面一致，减轻部分机型圆角处仍透出黄色 window 底的问题
        window.decorView.post {
            window.setBackgroundDrawableResource(R.color.background_dark)
        }
    }
}

package com.lemon.yingshi.tv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.lemon.yingshi.tv.R
import com.lemon.yingshi.tv.ui.navigation.LomenTVNavigation
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.LomenTVTheme
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            LomenTVTheme {
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(400)
                    showSplash = false
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundDark)
                ) {
                    LomenTVNavigation()
                    if (showSplash) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(PrimaryYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.splash_logo),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(160.dp)
                            )
                        }
                    }
                }
            }
        }

        window.decorView.post {
            window.setBackgroundDrawableResource(R.color.background_dark)
        }
    }
}

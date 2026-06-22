package com.littlebridge.enrollplus

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    private val contentReady = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        enableEdgeToEdge(
            statusBarStyle = if (isDarkMode) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )

            },
            navigationBarStyle = if (isDarkMode) {
                SystemBarStyle.dark(
                    android.graphics.Color.TRANSPARENT
                )
            } else {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            }
        )
        splashScreen.setKeepOnScreenCondition { !contentReady.value }

        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val iconView = try {
                splashScreenViewProvider.iconView
            } catch (_: Exception) {
                null
            }

            val targetView = iconView ?: splashScreenViewProvider.view
            val fade = ObjectAnimator.ofFloat(targetView, View.ALPHA, 1f, 0f)
            val scaleX = ObjectAnimator.ofFloat(targetView, View.SCALE_X, 1f, 1.12f)
            val scaleY = ObjectAnimator.ofFloat(targetView, View.SCALE_Y, 1f, 1.12f)

            listOf(fade, scaleX, scaleY).forEach {
                it.interpolator = AnticipateInterpolator()
                it.duration = 280L
            }

            fade.doOnEnd { splashScreenViewProvider.remove() }

            fade.start()
            scaleX.start()
            scaleY.start()
        }

        setContent {
            App(onContentRendered = { contentReady.value = true })
        }
    }
}

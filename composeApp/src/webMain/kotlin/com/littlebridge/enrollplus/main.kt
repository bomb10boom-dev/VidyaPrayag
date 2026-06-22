package com.littlebridge.enrollplus

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.littlebridge.enrollplus.di.initKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()
    
    ComposeViewport {
        App()
    }
}

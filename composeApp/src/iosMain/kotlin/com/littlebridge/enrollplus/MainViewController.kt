package com.littlebridge.enrollplus

import androidx.compose.ui.window.ComposeUIViewController
import com.littlebridge.enrollplus.di.initKoin

fun MainViewController() = ComposeUIViewController { 
    // Initialize Koin for iOS
    initKoin()
    App() 
}
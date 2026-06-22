package com.littlebridge.enrollplus

import android.os.Build
import okio.Path
import okio.Path.Companion.toPath

class AndroidPlatform(override val cacheDir: Path) : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform("/dev/null".toPath())
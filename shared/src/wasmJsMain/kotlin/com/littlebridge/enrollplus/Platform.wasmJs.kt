package com.littlebridge.enrollplus

import okio.Path
import okio.Path.Companion.toPath

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override val cacheDir: Path = "/dev/null".toPath()
}

actual fun getPlatform(): Platform = WasmPlatform()
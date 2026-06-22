package com.littlebridge.enrollplus

import okio.Path
import okio.Path.Companion.toPath

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val cacheDir: Path = System.getProperty("java.io.tmpdir").toPath()
}

actual fun getPlatform(): Platform = JVMPlatform()
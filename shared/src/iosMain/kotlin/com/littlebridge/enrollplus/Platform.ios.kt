package com.littlebridge.enrollplus

import platform.UIKit.UIDevice
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val cacheDir: Path = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first().toString().toPath()
}

actual fun getPlatform(): Platform = IOSPlatform()
package com.littlebridge.enrollplus.util

actual object AppLogger {
    actual fun d(tag: String, message: String) {
        console.log("DEBUG: [$tag] $message")
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        console.error("ERROR: [$tag] $message", throwable)
    }
}

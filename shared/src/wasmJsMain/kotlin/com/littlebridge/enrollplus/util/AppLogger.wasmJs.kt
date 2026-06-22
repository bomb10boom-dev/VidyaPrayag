package com.littlebridge.enrollplus.util

actual object AppLogger {
    actual fun d(tag: String, message: String) {
        println("DEBUG: [$tag] $message")
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        println("ERROR: [$tag] $message")
    }
}

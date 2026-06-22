package com.littlebridge.enrollplus.util

object AppConfig {
    // Centralized access to the base URLs for all services
    val authBaseUrl: String get() = Config.authBaseUrl
    val schoolBaseUrl: String get() = Config.schoolBaseUrl
}

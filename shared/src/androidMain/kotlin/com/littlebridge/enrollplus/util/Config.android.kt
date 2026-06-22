package com.littlebridge.enrollplus.util

import com.littlebridge.enrollplus.shared.BuildConfig

actual object Config {
    actual val authBaseUrl: String = BuildConfig.AUTH_BASE_URL
    actual val schoolBaseUrl: String = BuildConfig.SCHOOL_BASE_URL
    actual val isDev: Boolean = BuildConfig.IS_DEV
}

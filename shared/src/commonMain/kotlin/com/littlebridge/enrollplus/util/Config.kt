package com.littlebridge.enrollplus.util

expect object Config {
    val authBaseUrl: String
    val schoolBaseUrl: String

    /**
     * True only for the `dev` Android flavor. Drives the in-app debug backend
     * banner so the developer can see at a glance which backend the phone is
     * hitting (laptop vs Render). Always false on non-Android targets.
     */
    val isDev: Boolean
}

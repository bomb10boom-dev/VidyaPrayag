/*
 * File: OtpHttpClient.kt
 * Module: feature.auth.delivery
 *
 * Shared Ktor HTTP client used by EVERY HTTP-based OTP provider
 * (Fast2SMS, MSG91, Twilio, WhatsApp Cloud, generic webhook).
 *
 * Design notes
 * ------------
 *  - Singleton — Ktor clients are heavy (thread pools, connection cache);
 *    making one per provider would leak resources on every send.
 *  - Aggressive but bounded timeouts (5 s connect, 8 s request, 15 s socket)
 *    — an OTP that takes 30 s to deliver is useless; we want fast failover
 *    to the next provider in the chain.
 *  - Built-in retries are deliberately DISABLED. Provider retries are the
 *    dispatcher's job (so it can switch channels, not just hammer the
 *    same dead endpoint).
 *  - Logging level = NONE so we never echo the OTP code to stdout on a
 *    debug build. Provider files do their own structured logging that
 *    omits the code.
 */
package com.littlebridge.enrollplus.feature.auth.delivery

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal object OtpHttpClient {

    /** Json codec — lenient because every SMS gateway has its own quirks. */
    val jsonCodec: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * One client shared by every provider. Don't ever call .close() on it
     * — JVM shutdown handles cleanup. Closing it would break subsequent
     * OTP sends until the JVM is restarted.
     */
    val client: HttpClient by lazy {
        val codec = jsonCodec  // capture into a plain local so the install
                               // lambda's resolution of `json(...)` is
                               // unambiguous (Ktor's `json` function vs our
                               // property name).
        HttpClient(CIO) {
            expectSuccess = false  // we inspect statuses manually per provider
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 8_000
                socketTimeoutMillis = 15_000
            }
            install(ContentNegotiation) { json(codec) }
            install(Logging) { level = LogLevel.NONE }  // privacy: no code in logs
        }
    }
}

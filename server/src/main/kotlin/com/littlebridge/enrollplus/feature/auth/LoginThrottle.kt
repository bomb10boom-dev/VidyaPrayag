/*
 * File: LoginThrottle.kt
 * Module: feature.auth
 *
 * RA-41: a lightweight, in-memory sliding-window throttle for the email/password
 * login path. The OTP path is already rate-limited (OtpService resend cap +
 * lockout), but `/login` with email+password had NO throttle, leaving it open to
 * unthrottled credential stuffing.
 *
 * Design:
 *   - Two independent windows are checked per attempt: one keyed by client IP
 *     and one keyed by the (normalised) identifier. A request is blocked if
 *     EITHER window is over its cap — this throttles both "spray many accounts
 *     from one IP" and "hammer one account from many IPs".
 *   - A successful login clears the identifier window so a legitimate user who
 *     finally types the right password isn't penalised by their earlier typos.
 *   - Sliding window: timestamps older than the window are dropped on each touch,
 *     so there is no fixed-bucket boundary an attacker can game.
 *   - In-memory + ConcurrentHashMap: process-local, zero new dependencies and no
 *     schema change. For a single-instance deployment this is sufficient; a
 *     multi-instance deployment can later swap this for a shared store behind the
 *     same `check`/`recordFailure`/`recordSuccess` surface.
 *
 * Tunables (env, with safe defaults):
 *   LOGIN_MAX_ATTEMPTS      max failed attempts per window per key   (default 8)
 *   LOGIN_WINDOW_SECONDS    sliding-window length in seconds         (default 900 = 15m)
 */
package com.littlebridge.enrollplus.feature.auth

import java.util.concurrent.ConcurrentHashMap

object LoginThrottle {

    private fun envInt(name: String, default: Int, min: Int, max: Int): Int =
        System.getenv(name)?.trim()?.toIntOrNull()?.coerceIn(min, max) ?: default

    private val maxAttempts: Int by lazy { envInt("LOGIN_MAX_ATTEMPTS", 8, 3, 100) }
    private val windowMillis: Long by lazy { envInt("LOGIN_WINDOW_SECONDS", 900, 30, 86_400).toLong() * 1000L }

    // key -> recent FAILED-attempt timestamps (epoch millis), newest last.
    private val hits = ConcurrentHashMap<String, MutableList<Long>>()

    private fun ipKey(ip: String?) = "ip:" + (ip?.takeIf { it.isNotBlank() } ?: "unknown")
    private fun idKey(identifier: String) = "id:" + identifier.lowercase()

    /** Drop timestamps older than the window; returns the surviving count. */
    private fun pruneAndCount(key: String, now: Long): Int {
        val cutoff = now - windowMillis
        val list = hits[key] ?: return 0
        synchronized(list) {
            list.removeAll { it < cutoff }
            if (list.isEmpty()) hits.remove(key, list)
            return list.size
        }
    }

    /**
     * Returns true if this attempt is currently throttled (EITHER the IP window
     * or the identifier window is at/over the cap). Read-only: does not record.
     */
    fun isThrottled(ip: String?, identifier: String): Boolean {
        val now = System.currentTimeMillis()
        return pruneAndCount(ipKey(ip), now) >= maxAttempts ||
            pruneAndCount(idKey(identifier), now) >= maxAttempts
    }

    /** Record a FAILED login attempt against both the IP and identifier windows. */
    fun recordFailure(ip: String?, identifier: String) {
        val now = System.currentTimeMillis()
        for (key in listOf(ipKey(ip), idKey(identifier))) {
            val list = hits.getOrPut(key) { mutableListOf() }
            synchronized(list) {
                val cutoff = now - windowMillis
                list.removeAll { it < cutoff }
                list.add(now)
            }
        }
    }

    /**
     * Clear the identifier window after a SUCCESSFUL login so earlier typos don't
     * keep a legitimate user locked out. The IP window is intentionally NOT
     * cleared — one good login from an IP shouldn't wipe the spray evidence.
     */
    fun recordSuccess(identifier: String) {
        hits.remove(idKey(identifier))
    }
}

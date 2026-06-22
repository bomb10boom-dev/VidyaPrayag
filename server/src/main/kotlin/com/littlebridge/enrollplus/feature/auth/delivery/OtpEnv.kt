/*
 * File: OtpEnv.kt
 * Module: feature.auth.delivery
 *
 * Tiny env-var helper that ALL provider files use. Centralised so we
 * can swap to a different config source later (Consul, Doppler, AWS
 * Secrets Manager, ...) without touching every provider.
 *
 * Rules (enforced here, not in callers):
 *   - Blank ("") env values are treated as unset.
 *   - Trim whitespace and surrounding quotes — copy-pasted secrets from
 *     dashboards often arrive with stray "..." wrappers.
 *   - Never log the resolved value. Callers use the returned value
 *     directly to authenticate against a third-party API; if we logged
 *     it we'd leak the key.
 */
package com.littlebridge.enrollplus.feature.auth.delivery

internal object OtpEnv {

    fun get(name: String): String? {
        val raw = System.getenv(name) ?: return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // strip exactly one pair of surrounding quotes if present
        return when {
            trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"') ->
                trimmed.substring(1, trimmed.length - 1)
            trimmed.length >= 2 && trimmed.startsWith('\'') && trimmed.endsWith('\'') ->
                trimmed.substring(1, trimmed.length - 1)
            else -> trimmed
        }
    }

    fun get(name: String, default: String): String = get(name) ?: default

    fun getBool(name: String, default: Boolean): Boolean =
        get(name)?.equals("true", ignoreCase = true) ?: default

    fun getInt(name: String, default: Int): Int =
        get(name)?.toIntOrNull() ?: default

    fun getList(name: String, default: List<String>): List<String> {
        val raw = get(name) ?: return default
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Are ALL of these env vars set (non-blank)? Used by `isConfigured()`. */
    fun allSet(vararg names: String): Boolean = names.all { get(it) != null }
}

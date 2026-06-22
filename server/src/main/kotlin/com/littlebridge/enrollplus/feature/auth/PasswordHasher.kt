/*
 * File: PasswordHasher.kt
 * Module: feature.auth
 *
 * Purpose:
 *   Replaces the previous insecure, unsalted `sha256Hex("pwd:$p")` password
 *   hashing (audit §3.3, finding E) with a proper, salted, work-factored KDF.
 *
 *   Uses PBKDF2WithHmacSHA256 from the JDK (javax.crypto) — no new third-party
 *   dependency required, so the server build is unaffected. Each password gets
 *   a fresh 16-byte cryptographically-random salt and a high iteration count,
 *   defeating rainbow-table and GPU bulk-cracking attacks.
 *
 * Stored format (single column `app_users.password_hash`):
 *   pbkdf2$<iterations>$<base64-salt>$<base64-derived-key>
 *
 * Backward compatibility:
 *   `verify()` still recognises the legacy `sha256Hex("pwd:$p")` hashes that
 *   exist in already-provisioned databases, so old logins keep working. New
 *   signups and password writes use PBKDF2. Callers that want to transparently
 *   upgrade a legacy hash on successful login can check `needsRehash()`.
 */
package com.littlebridge.enrollplus.feature.auth

import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {

    private const val ALGO = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val PREFIX = "pbkdf2"

    private val secureRandom = SecureRandom()

    /** Produce a salted PBKDF2 hash string for storage. */
    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val dk = pbkdf2(password, salt, ITERATIONS, KEY_BITS)
        val b64 = Base64.getEncoder()
        return "$PREFIX\$$ITERATIONS\$${b64.encodeToString(salt)}\$${b64.encodeToString(dk)}"
    }

    /**
     * Constant-time verify. Accepts both the new PBKDF2 format and the legacy
     * unsalted `sha256Hex("pwd:$p")` format so existing accounts keep working.
     */
    fun verify(password: String, stored: String?): Boolean {
        if (stored.isNullOrBlank()) return false
        return if (stored.startsWith("$PREFIX\$")) {
            verifyPbkdf2(password, stored)
        } else {
            // Legacy: unsalted SHA-256 with static prefix.
            constantTimeEquals(legacySha256(password), stored)
        }
    }

    /** True if the stored hash is in the legacy format and should be upgraded. */
    fun needsRehash(stored: String?): Boolean =
        stored != null && !stored.startsWith("$PREFIX\$")

    private fun verifyPbkdf2(password: String, stored: String): Boolean {
        val parts = stored.split("$")
        if (parts.size != 4) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        val actual = pbkdf2(password, salt, iterations, expected.size * 8)
        return constantTimeEquals(actual, expected)
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyBits)
        return SecretKeyFactory.getInstance(ALGO).generateSecret(spec).encoded
    }

    private fun legacySha256(p: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest("pwd:$p".toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
        return r == 0
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        constantTimeEquals(a.toByteArray(), b.toByteArray())
}

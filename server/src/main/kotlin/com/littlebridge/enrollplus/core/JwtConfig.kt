/*
 * File: JwtConfig.kt
 * Module: core
 * Purpose:
 *   Centralised HMAC256 JWT issuance + verification for VidyaPrayag.
 *   Wraps com.auth0:java-jwt (transitively pulled in by ktor-server-auth-jwt).
 *
 * Reads (with safe dev defaults):
 *   - JWT_SECRET       → HMAC signing key (REQUIRED in production)
 *   - JWT_ISSUER       → default "vidyaprayag-api"
 *   - JWT_AUDIENCE     → default "vidyaprayag-app"
 *   - JWT_REALM        → default "vidyaprayag"
 *   - JWT_EXPIRY_SECS  → default 7 days for access token
 *
 * Token claims:
 *   - sub        : userId (UUID string)
 *   - role       : ADMIN | PARENT | TEACHER
 *   - name       : display name (convenience)
 *
 * Used by:
 *   - feature/auth/AuthRouting.kt    (signup, login → issues token)
 *   - core/SecurityModule.kt         (installs Ktor JWT auth)
 *   - any handler that does `call.principalUserId()`
 *
 * NOTE FOR DEVOPS (manual step you must do):
 *   Set JWT_SECRET to a strong random 256-bit value in production .env.
 *   The dev fallback ("vidyaprayag-dev-secret-change-me") is INSECURE and is
 *   only there so the server boots on a fresh clone.
 */
package com.littlebridge.enrollplus.core

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

object JwtConfig {
    private const val DEV_SECRET_FALLBACK = "vidyaprayag-dev-secret-change-me"

    private fun env(name: String, default: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

    private fun rawEnv(name: String): String? =
        System.getenv(name)?.takeIf { it.isNotBlank() }

    /**
     * Treat the deployment as production whenever a real Postgres database is
     * configured (Render/Supabase set DATABASE_URL). This mirrors
     * DatabaseFactory's own prod detection so we have a single, consistent
     * signal instead of inventing a new env var.
     */
    private val isProduction: Boolean
        get() = rawEnv("DATABASE_URL") != null

    /**
     * Resolve the signing secret, HARD-FAILING the boot in production if it is
     * unset or still the public dev default (audit §3.2, finding E). In dev
     * (SQLite, no DATABASE_URL) we fall back to the well-known dev secret so a
     * fresh clone still boots.
     */
    val secret: String by lazy {
        val configured = rawEnv("JWT_SECRET")
        if (isProduction && (configured == null || configured == DEV_SECRET_FALLBACK)) {
            throw IllegalStateException(
                "FATAL: JWT_SECRET must be set to a strong, unique value in production " +
                "(DATABASE_URL is configured). Refusing to boot with a missing or default " +
                "signing key — this would allow trivial token forgery."
            )
        }
        configured ?: DEV_SECRET_FALLBACK
    }

    val issuer: String   by lazy { env("JWT_ISSUER", "vidyaprayag-api") }
    val audience: String by lazy { env("JWT_AUDIENCE", "vidyaprayag-app") }
    val realm: String    by lazy { env("JWT_REALM", "vidyaprayag") }
    // Default access-token TTL shortened to 1 day (was 7) to limit the blast
    // radius of a leaked/forged token. Refresh tokens cover longer sessions.
    val expirySecs: Long by lazy { env("JWT_EXPIRY_SECS", "86400").toLong() } // 1 day

    private val algorithm by lazy { Algorithm.HMAC256(secret) }

    val verifier: com.auth0.jwt.JWTVerifier by lazy {
        JWT.require(algorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .build()
    }

    /** Issue a signed access token. */
    fun issueToken(userId: String, role: String, name: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId)
            .withClaim("role", role)
            .withClaim("name", name)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + expirySecs * 1000))
            .sign(algorithm)

    /** Issue an opaque refresh token. In production, persist + rotate it. */
    fun issueRefreshToken(userId: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId)
            .withClaim("type", "refresh")
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 3600 * 1000)) // 30 days
            .sign(algorithm)
}

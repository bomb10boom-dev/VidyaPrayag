/*
 * File: SecurityModule.kt
 * Module: core
 * Purpose:
 *   Installs the Ktor Authentication plugin with a "jwt" provider configured
 *   from JwtConfig. Also exposes the small helpers used by routes:
 *     - call.principalUserId()
 *     - call.principalRole()
 *
 * Used by:
 *   - Application.kt → install(Authentication) { configureJwt() }
 *   - Every protected route wraps its block in `authenticate("jwt") { ... }`.
 *
 * Spec ref:
 *   - vidya_prayag_api_spec.artifact.md §Authentication "Bearer JWT"
 *   - vidya_prayag_api_spec2.artifact.md §Headers "Authorization: Bearer ..."
 */
package com.littlebridge.enrollplus.core

import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

/** Apply to `install(Authentication) { configureJwt() }`. */
fun AuthenticationConfig.configureJwt(name: String = "jwt") {
    jwt(name) {
        realm = JwtConfig.realm
        verifier(JwtConfig.verifier)
        validate { credential ->
            val sub = credential.payload.subject
            if (sub.isNullOrBlank()) return@validate null
            // RA-34: enforce is_active on EVERY authenticated request — this is the
            // single root kill-switch covering all roles, including parent routes
            // that read principalUserId() directly without a school/teacher guard.
            // Deactivating a user (is_active=false) instantly invalidates every
            // already-issued access token on its next use.
            val uid = runCatching { UUID.fromString(sub) }.getOrNull() ?: return@validate null
            val active = dbQuery {
                AppUsersTable.selectAll().where { AppUsersTable.id eq uid }
                    .singleOrNull()?.get(AppUsersTable.isActive)
            }
            // Unknown user (no row) or deactivated → reject the principal.
            if (active != true) null else JWTPrincipal(credential.payload)
        }
        challenge { _, _ ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiError(message = "Session expired, please login again", errorCode = "UNAUTHORIZED")
            )
        }
    }
}

/** Returns the authenticated user's UUID string from JWT `sub`, or null if absent. */
fun ApplicationCall.principalUserId(): String? =
    principal<JWTPrincipal>()?.payload?.subject

/** Returns the authenticated user's role claim, or null. */
fun ApplicationCall.principalRole(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()

/** Returns the authenticated user's display name claim, or null. */
fun ApplicationCall.principalName(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("name")?.asString()

package com.littlebridge.enrollplus.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider

/**
 * RA-S01 (TIER -1, SECURITY) — clears the Ktor [Auth] plugin's in-memory bearer-token cache.
 *
 * The [HttpClient] is a process-wide Koin `single`. The `Auth { bearer { … } }` block installs a
 * [BearerAuthProvider] that caches the [io.ktor.client.plugins.auth.providers.BearerTokens] resolved
 * by `loadTokens`. Clearing DataStore (`PreferenceRepository.clearSession()`) on logout does **not**
 * evict that cache — the cached tokens survive because the singleton client is never recreated.
 *
 * Without this, a logout → re-login (especially as a *different* role on the same device) could sign
 * the first authenticated requests with the **previous** user's bearer token until a 401 forces a
 * refresh — cross-role data exposure / "ghost session" bleed.
 *
 * [clearAuthCache] invokes `clearToken()` on every installed [BearerAuthProvider], forcing the next
 * request to re-run `loadTokens` (which, after `clearSession()`, returns `null` → no Authorization
 * header on a logged-out client).
 */
class SessionManager(
    private val client: HttpClient,
) {
    /** Evict every cached bearer token so the next request re-resolves from the (now-cleared) store. */
    fun clearAuthCache() {
        // Ktor 3.x: the installed `Auth` plugin instance returned by `client.plugin(Auth)` does NOT
        // expose its providers publicly — only `AuthConfig.providers` (the config block) does, which
        // is not accessible post-install. The public, supported way to reach the live providers is the
        // `HttpClient.authProviders` extension property (snapshot list stored in client attributes).
        runCatching {
            client.authProviders
                .filterIsInstance<BearerAuthProvider>()
                .forEach { provider -> provider.clearToken() }
        }
    }
}

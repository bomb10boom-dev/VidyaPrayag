/*
 * File: FirebaseAdminInitializer.kt
 * Module: feature.auth.gateway
 *
 * Owns the lifecycle of the single FirebaseApp instance the gateway flow uses
 * to push FCM data messages to registered OTPSender Android devices.
 *
 * Credential sources (checked in this order — first non-blank wins):
 *   1. FIREBASE_CREDENTIALS_PATH  — absolute path to a service-account JSON
 *      file downloaded from the Firebase console (Project Settings → Service
 *      Accounts → Generate new private key). RECOMMENDED for Render / Docker.
 *   2. GOOGLE_APPLICATION_CREDENTIALS — the GCP-standard ADC env var; same
 *      semantics (path to a service-account JSON). Supported because every
 *      GCP/ Firebase tool already honours it, so operators can reuse an
 *      existing secret without a second env var.
 *   3. Application Default Credentials (ADC) — `GoogleCredentials
 *      .getApplicationDefault()`. Works on GCP metadata servers and anywhere
 *      `gcloud auth application-default login` has been run. NOT available on
 *      a fresh Render dyno, hence the explicit path vars above.
 *
 * Idempotent + thread-safe: [ensureInitialized] may be called concurrently
 * from multiple coroutines; only the first call boots the SDK, the rest get
 * the cached [FirebaseApp]. A failure to initialise (bad file, bad JSON) is
 * caught and remembered so we don't retry on every send — [isConfigured] /
 * [ensureInitialized] return null thereafter and the caller leaves the SMS
 * request PENDING for recovery.
 *
 * No Koin/DI — this is an `object` singleton, matching every other helper in
 * the auth feature.
 */
package com.littlebridge.vidyaprayag.feature.auth.gateway

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object FirebaseAdminInitializer {

    private val log = LoggerFactory.getLogger("FirebaseAdminInitializer")

    private const val APP_NAME = "vidyaprayag-otp-gateway"

    /**
     * True iff at least one credential source is plausibly available. This is
     * a CHEAP check (env presence + file existence for the path vars) — it does
     * NOT open the stream or parse JSON, so it's safe to call from
     * non-suspend, hot paths. A true return is not a guarantee that
     * [ensureInitialized] will succeed (the file could be malformed), but a
     * false return is a guarantee that it won't.
     */
    fun isConfigured(): Boolean = credentialsFilePath() != null || adcLikelyAvailable()

    /**
     * Boot the FirebaseApp if not already booted and a credential source is
     * available. Returns the app on success, or null if no credentials are
     * configured or the last boot attempt failed (failure is sticky). Safe to
     * call repeatedly.
     */
    fun ensureInitialized(): FirebaseApp? {
        // Already booted on a previous call (this JVM).
        FirebaseApp.getApps().firstOrNull { it.name == APP_NAME }?.let { return it }

        synchronized(bootLock) {
            // Double-check after acquiring the lock — another thread may have
            // booted while we waited.
            FirebaseApp.getApps().firstOrNull { it.name == APP_NAME }?.let { return it }
            if (bootFailed) return null

            val app = runCatching { boot() }.getOrElse { t ->
                bootFailed = true
                log.error(
                    "[Firebase] failed to initialise FirebaseApp — FCM push disabled: {}",
                    "${t.javaClass.simpleName}: ${t.message?.take(200)}",
                )
                null
            }
            return app
        }
    }

    // ---- internals --------------------------------------------------------

    private val bootLock = Any()
    @Volatile
    private var bootFailed = false

    /** The first non-blank of FIREBASE_CREDENTIALS_PATH / GOOGLE_APPLICATION_CREDENTIALS, as a File if it exists. */
    private fun credentialsFilePath(): File? {
        val envPath = System.getenv("FIREBASE_CREDENTIALS_PATH")
            ?.takeIf { it.isNotBlank() }
            ?: System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
                ?.takeIf { it.isNotBlank() }
            ?: return null
        val file = File(envPath)
        return file.takeIf { it.isFile }
    }

    /**
     * Heuristic for ADC availability. On a real GCP/Cloud Run/Compute host the
     * metadata server is reachable; on Render/Docker/local it usually is not
     * unless the operator ran `gcloud auth application-default login`. We can't
     * cheaply know that without a network probe (which we refuse to do here),
     * so we optimistically return true and let [boot] fail fast if ADC is in
     * fact absent. This keeps the path-var-first ordering honest: if neither
     * path env is set we still TRY ADC rather than silently disabling FCM.
     */
    private fun adcLikelyAvailable(): Boolean = true

    private fun boot(): FirebaseApp {
        val pathFile = credentialsFilePath()
        val credentials: GoogleCredentials = if (pathFile != null) {
            log.info("[Firebase] loading service-account credentials from: {}", pathFile.absolutePath)
            FileInputStream(pathFile).use { stream: InputStream ->
                GoogleCredentials.fromStream(stream)
            }
        } else {
            log.info("[Firebase] no credential path env set; trying Application Default Credentials")
            GoogleCredentials.getApplicationDefault()
        }

        val optionsBuilder = FirebaseOptions.builder()
            .setCredentials(credentials)

        // Project id is optional when using a service-account JSON (the JSON
        // carries it), but ADC sometimes needs it. Read FIREBASE_PROJECT_ID if
        // provided; otherwise let the SDK infer from the credentials.
        System.getenv("FIREBASE_PROJECT_ID")?.takeIf { it.isNotBlank() }?.let { pid ->
            optionsBuilder.setProjectId(pid)
        }

        val options = optionsBuilder.build()
        val app = FirebaseApp.initializeApp(options, APP_NAME)
        log.info("[Firebase] FirebaseApp '{}' initialised successfully", APP_NAME)
        return app
    }
}

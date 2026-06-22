/*
 * File: FirebaseAdminInitializer.kt
 * Module: feature.notification.firebase
 *
 * Single, lazy initialiser for the Firebase Admin SDK on the server side.
 *
 * WHY A DEDICATED INITIALIZER
 *   The Admin SDK only needs to be initialised ONCE per JVM. Scattering
 *   FirebaseApp.initializeApp() calls across services is a recipe for
 *   double-init exceptions and for credentials leaking into logs. This
 *   object is the ONLY sanctioned entry point — NotificationService calls
 *   [app] to obtain the initialised FirebaseApp (or null when credentials
 *   are absent).
 *
 * CREDENTIAL RESOLUTION (first non-blank wins):
 *   1. FIREBASE_CREDENTIALS_FILE  → absolute path to a service-account JSON
 *                                   on disk (Render / Docker secret mount).
 *   2. FIREBASE_CREDENTIALS_JSON  → the full service-account JSON content in
 *                                   an env var (handy for one-off deploys
 *                                   without a mounted file).
 *   3. GOOGLE_APPLICATION_CREDENTIALS → Google ADC convention (the Admin
 *                                   SDK's own fallback path; we delegate to
 *                                   FirebaseOptions.builder().setCredentials()
 *                                   with ApplicationDefault via getApplicationDefault()).
 *
 * GRACEFUL DEGRADATION
 *   When none of the above resolve, the initializer logs a single WARNING
 *   and [app] returns null. NotificationService then degrades to a no-op
 *   (returns sent=0) instead of crashing the boot. This lets local dev
 *   (SQLite, no Firebase project) run the rest of the API surface unchanged
 *   and lets the migration ship without forcing every operator to wire
 *   Firebase before the next deploy.
 *
 * MULTI-PROJECT SUPPORT (feature/setup_notification — OTPSender integration)
 *   The backend now pushes to TWO independent Firebase projects:
 *     • The Enroll / VidyaPrayag app  → [app] (the original FCM project).
 *     • The OTPSender gateway app     → [otpSenderApp] (a SEPARATE Firebase
 *                                       project that owns the OTPSender
 *                                       Android install's FCM tokens).
 *   Each project needs its OWN service-account credential. Rather than
 *   replacing the existing single-app mechanism, we EXTEND it: a second
 *   resolver chain reads OTP_SENDER_*-prefixed env vars (mirroring the
 *   FIREBASE_* family) and initialises a distinct, named FirebaseApp. When
 *   the OTPSender credentials are absent, [otpSenderApp] returns null and the
 *   gateway dispatch degrades to "leave the SMS request pending" — exactly the
 *   same no-throw degradation the primary app already uses.
 */
package com.littlebridge.enrollplus.feature.notification.firebase

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.File
import java.io.FileInputStream
import java.util.Properties


object FirebaseAdminInitializer {

    private const val APP_NAME = "vidyaprayag-server"
    private const val OTP_SENDER_APP_NAME = "vidyaprayag-otpsender"

    @Volatile
    private var cachedApp: FirebaseApp? = null

    @Volatile
    private var attempted: Boolean = false

    @Volatile
    private var credentialSource: String? = null

    // -- OTPSender (separate Firebase project) ----------------------------
    @Volatile
    private var cachedOtpSenderApp: FirebaseApp? = null

    @Volatile
    private var otpSenderAttempted: Boolean = false

    @Volatile
    private var otpSenderCredentialSource: String? = null

    /**
     * Returns the initialized FirebaseApp or null when credentials
     * cannot be resolved.
     */
    fun app(): FirebaseApp? {
        if (attempted) return cachedApp

        synchronized(this) {
            if (attempted) return cachedApp

            cachedApp = initialise(
                appName = APP_NAME,
                envKeys = PRIMARY_ENV_KEYS,
                localPropertyKey = "firebase.credentials.file",
                onResolved = { credentialSource = it },
            )
            attempted = true

            if (cachedApp == null) {
                println(
                    "FIREBASE_INIT: No credentials resolved — push dispatch DISABLED."
                )
            } else {
                println(
                    "FIREBASE_INIT: FirebaseApp '${cachedApp!!.name}' initialized using '$credentialSource'."
                )
            }

            return cachedApp
        }
    }

    fun isAvailable(): Boolean = app() != null

    /**
     * Returns the initialized OTPSender FirebaseApp or null when its
     * credentials cannot be resolved. This is a DISTINCT Firebase project
     * from [app] — it owns the OTPSender Android install's FCM tokens. When
     * null, the gateway dispatch leaves the SMS request pending (no throw).
     */
    fun otpSenderApp(): FirebaseApp? {
        if (otpSenderAttempted) return cachedOtpSenderApp

        synchronized(this) {
            if (otpSenderAttempted) return cachedOtpSenderApp

            cachedOtpSenderApp = initialise(
                appName = OTP_SENDER_APP_NAME,
                envKeys = OTP_SENDER_ENV_KEYS,
                localPropertyKey = "otp.sender.firebase.credentials.file",
                onResolved = { otpSenderCredentialSource = it },
            )
            otpSenderAttempted = true

            if (cachedOtpSenderApp == null) {
                println(
                    "FIREBASE_INIT: No OTPSender credentials resolved — SMS-gateway push DISABLED."
                )
            } else {
                println(
                    "FIREBASE_INIT: OTPSender FirebaseApp '${cachedOtpSenderApp!!.name}' initialized using '$otpSenderCredentialSource'."
                )
            }

            return cachedOtpSenderApp
        }
    }

    fun isOtpSenderAvailable(): Boolean = otpSenderApp() != null

    // ------------------------------------------------------------------
    // Initialization
    // ------------------------------------------------------------------

    /**
     * Initialise a named FirebaseApp from the supplied env-key family +
     * local.properties key. Shared by both the primary app and the OTPSender
     * app so the credential-resolution policy stays in ONE place.
     */
    private fun initialise(
        appName: String,
        envKeys: FirebaseEnvKeys,
        localPropertyKey: String,
        onResolved: (String) -> Unit,
    ): FirebaseApp? {

        FirebaseApp.getApps()
            .firstOrNull { it.name == appName }
            ?.let {
                println("FIREBASE_INIT: Existing FirebaseApp '$appName' found.")
                return it
            }

        val credentials = resolveCredentials(envKeys, localPropertyKey, onResolved)
            ?: return null

        val options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .build()

        return runCatching {
            FirebaseApp.initializeApp(options, appName)
        }.onSuccess {
            println("FIREBASE_INIT: Firebase Admin SDK initialized for '$appName'.")
        }.onFailure {
            println(
                "FIREBASE_INIT: FirebaseApp.initializeApp failed for '$appName': ${it.message}"
            )
        }.getOrNull()
    }

    /**
     * The env-var family for one Firebase project. The primary app reads the
     * historical FIREBASE_* names; the OTPSender app reads OTP_SENDER_*-
     * prefixed equivalents so the two projects never share a credential.
     */
    private data class FirebaseEnvKeys(
        val json: String,
        val file: String,
        val googleAppCreds: String?,
    )

    private val PRIMARY_ENV_KEYS = FirebaseEnvKeys(
        json = "FIREBASE_CREDENTIALS_JSON",
        file = "FIREBASE_CREDENTIALS_FILE",
        googleAppCreds = "GOOGLE_APPLICATION_CREDENTIALS",
    )

    private val OTP_SENDER_ENV_KEYS = FirebaseEnvKeys(
        json = "OTP_SENDER_FIREBASE_CREDENTIALS_JSON",
        file = "OTP_SENDER_FIREBASE_CREDENTIALS_FILE",
        // No ADC fallback for the OTPSender project: ADC is process-global and
        // would otherwise resolve to the PRIMARY project's credentials, silently
        // pushing OTPSender messages to the wrong Firebase app.
        googleAppCreds = null,
    )

    // ------------------------------------------------------------------
    // Credential Resolution
    // ------------------------------------------------------------------

    private fun resolveCredentials(
        envKeys: FirebaseEnvKeys,
        localPropertyKey: String,
        onResolved: (String) -> Unit,
    ): GoogleCredentials? {

        // --------------------------------------------------------------
        // 1. Render / Railway / Fly.io  (inline JSON env var)
        // --------------------------------------------------------------
        System.getenv(envKeys.json)
            ?.takeIf { it.isNotBlank() }
            ?.let { json ->

                println(
                    "FIREBASE_INIT: Attempting ${envKeys.json}"
                )

                return runCatching {
                    GoogleCredentials.fromStream(
                        json.byteInputStream(Charsets.UTF_8)
                    )
                }.onSuccess {
                    onResolved(envKeys.json)

                    println(
                        "FIREBASE_INIT: Loaded credentials from ${envKeys.json}"
                    )
                }.onFailure {
                    println(
                        "FIREBASE_INIT: Invalid ${envKeys.json}: ${it.message}"
                    )
                }.getOrNull()
            }

        // --------------------------------------------------------------
        // 2. Explicit credentials file
        // --------------------------------------------------------------
        System.getenv(envKeys.file)
            ?.takeIf { it.isNotBlank() }
            ?.let { path ->

                println(
                    "FIREBASE_INIT: Attempting ${envKeys.file}"
                )

                return runCatching {
                    FileInputStream(path).use {
                        GoogleCredentials.fromStream(it)
                    }
                }.onSuccess {
                    onResolved(envKeys.file)

                    println(
                        "FIREBASE_INIT: Loaded credentials from ${envKeys.file} ($path)"
                    )
                }.onFailure {
                    println(
                        "FIREBASE_INIT: Cannot read ${envKeys.file} ($path): ${it.message}"
                    )
                }.getOrNull()
            }

        // --------------------------------------------------------------
        // 3. Standard Google env var (primary app only; null for OTPSender)
        // --------------------------------------------------------------
        envKeys.googleAppCreds
            ?.let { System.getenv(it) }
            ?.takeIf { it.isNotBlank() }
            ?.let { path ->

                println(
                    "FIREBASE_INIT: Attempting ${envKeys.googleAppCreds}"
                )

                return runCatching {
                    FileInputStream(path).use {
                        GoogleCredentials.fromStream(it)
                    }
                }.onSuccess {
                    onResolved(envKeys.googleAppCreds!!)

                    println(
                        "FIREBASE_INIT: Loaded credentials from ${envKeys.googleAppCreds} ($path)"
                    )
                }.onFailure {
                    println(
                        "FIREBASE_INIT: Cannot read ${envKeys.googleAppCreds} ($path): ${it.message}"
                    )
                }.getOrNull()
            }

        // --------------------------------------------------------------
        // 4. local.properties
        // --------------------------------------------------------------
        localProperty(localPropertyKey)
            ?.takeIf { it.isNotBlank() }
            ?.let { path ->

                println(
                    "FIREBASE_INIT: Attempting local.properties credential ($localPropertyKey)"
                )

                return runCatching {
                    FileInputStream(path).use {
                        GoogleCredentials.fromStream(it)
                    }
                }.onSuccess {
                    onResolved("local.properties:$localPropertyKey")

                    println(
                        "FIREBASE_INIT: Loaded credentials from local.properties ($path)"
                    )
                }.onFailure {
                    println(
                        "FIREBASE_INIT: Cannot read local.properties credential file ($path): ${it.message}"
                    )
                }.getOrNull()
            }

        // --------------------------------------------------------------
        // 5. Application Default Credentials (primary app only)
        //    Skipped for the OTPSender project — see OTP_SENDER_ENV_KEYS note.
        // --------------------------------------------------------------
        if (envKeys.googleAppCreds == null) {
            println(
                "FIREBASE_INIT: No '$localPropertyKey'/${envKeys.json}/${envKeys.file} resolved; " +
                    "skipping ADC fallback (project-scoped credential required)."
            )
            return null
        }

        println(
            "FIREBASE_INIT: Attempting Application Default Credentials"
        )

        return runCatching {
            GoogleCredentials.getApplicationDefault()
        }.onSuccess {
            onResolved("Application Default Credentials")

            println(
                "FIREBASE_INIT: Loaded Application Default Credentials"
            )
        }.onFailure {
            println(
                "FIREBASE_INIT: Application Default Credentials unavailable (${it.message?.take(200)})"
            )

            println(
                """
                FIREBASE_INIT: No credentials resolved.

                Supported sources:
                - ${envKeys.json}
                - ${envKeys.file}
                - ${envKeys.googleAppCreds ?: "(ADC disabled for this project)"}
                - local.properties ($localPropertyKey)
                - Application Default Credentials
                """.trimIndent()
            )
        }.getOrNull()
    }

    // ------------------------------------------------------------------
    // local.properties
    // ------------------------------------------------------------------

    private fun localProperty(key: String): String? {
        val localProperties = findLocalProperties()

        if (localProperties == null) {
            println(
                """
            FIREBASE_INIT: local.properties not found.
            Searched from:
            ${System.getProperty("user.dir")}
            """.trimIndent()
            )
            return null
        }

        println(
            "FIREBASE_INIT: Found local.properties at ${localProperties.absolutePath}"
        )

        return runCatching {
            val props = Properties()

            localProperties.inputStream().use {
                props.load(it)
            }

            props.getProperty(key)
        }.onFailure {
            println(
                "FIREBASE_INIT: Failed reading local.properties: ${it.message}"
            )
        }.getOrNull()
    }

    private fun findLocalProperties(): File? {
        var current = File(System.getProperty("user.dir")).absoluteFile

        while (true) {

            val candidate = File(current, "local.properties")

            if (candidate.exists()) {
                return candidate
            }

            current = current.parentFile ?: break
        }

        return null
    }
}

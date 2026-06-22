import java.io.ByteArrayOutputStream
import java.time.Instant

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    application
}

// ---------------------------------------------------------------------------
// Build identity — capture the current git SHA + build time so the running
// server can report exactly which commit is deployed. This is what lets a
// phone screenshot prove whether it hit the laptop backend or stale Render.
// Resolution is best-effort: if git isn't available (e.g. Render shallow
// clone), we fall back to "unknown" instead of failing the build.
// ---------------------------------------------------------------------------
val gitSha: String = run {
    // 1) Prefer CI/PaaS-provided commit SHAs. On Render the build container is
    //    often a shallow checkout WITHOUT a usable `.git`, so `git rev-parse`
    //    silently returns "unknown" and /api/v1/config/version becomes useless
    //    for the very deploy-drift verification the report demands. Render
    //    always exports RENDER_GIT_COMMIT; GitHub Actions exports GITHUB_SHA.
    val envSha = sequenceOf(
        "RENDER_GIT_COMMIT",
        "GIT_COMMIT",
        "GITHUB_SHA",
        "SOURCE_COMMIT",
        "VIDYAPRAYAG_GIT_SHA"
    ).mapNotNull { System.getenv(it)?.trim()?.takeIf { sha -> sha.isNotBlank() } }
        .firstOrNull()
        ?.take(12)

    // 2) Fall back to a local `git` call (works for laptop builds).
    envSha ?: runCatching {
        val proc = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val out = ByteArrayOutputStream()
        proc.inputStream.copyTo(out)
        proc.waitFor()
        out.toString(Charsets.UTF_8.name()).trim().ifBlank { "unknown" }
    }.getOrDefault("unknown")
}
val buildTimeIso: String = Instant.now().toString()

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

group = "com.littlebridge.enrollplus"
version = "1.0.0"
application {
    mainClass.set("com.littlebridge.enrollplus.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf(
        "-Dio.ktor.development=$isDevelopment",
        // Surface build identity to the running process so /api/v1/config/version
        // can report which commit is live. Render/Docker also pick these up.
        "-Dvidyaprayag.git.sha=$gitSha",
        "-Dvidyaprayag.build.time=$buildTimeIso",
        "-Dvidyaprayag.version=$version"
    )
}

dependencies {
    // NOTE: `:server` no longer depends on `:shared`.
    //
    // The `:shared` module is a Kotlin Multiplatform module (Android + iOS +
    // JVM + JS + wasmJs, plus AGP, Compose Multiplatform, Room/KSP). Depending
    // on it forces a full KMP configuration on every Gradle build of `:server`,
    // which on a cold clone downloads gigabytes of artifacts and can take 30+
    // minutes on average hardware/internet. `:server` only used two trivial
    // symbols from `:shared` (SERVER_PORT, Greeting), which are now inlined in
    // `server/src/main/kotlin/com.littlebridge.enrollplus/ServerEntry.kt`.
    //
    // If you ever need to share more code between server and the mobile/web
    // apps, prefer creating a JVM-only sub-module (e.g. `:shared-jvm`) instead
    // of reintroducing the full multiplatform dependency here.
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation("io.ktor:ktor-server-content-negotiation:3.4.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.3")
    implementation("io.ktor:ktor-server-status-pages:3.4.3")
    implementation("io.ktor:ktor-server-cors:3.4.3")
    implementation("io.ktor:ktor-server-auth:3.4.3")
    implementation("io.ktor:ktor-server-auth-jwt:3.4.3")
    implementation("io.ktor:ktor-server-call-logging:3.4.3")
    implementation("io.ktor:ktor-server-auto-head-response:3.4.3")

    // -----------------------------------------------------------------
    // Ktor HTTP CLIENT — used by the OTP delivery layer (Fast2SMS, MSG91,
    // Twilio, WhatsApp Cloud API, generic webhook). Kept on the server
    // module only; the mobile/web apps have their own client config in
    // :shared.
    //
    // CIO engine is chosen because it's pure-Kotlin/JVM (no JNI native
    // libs) and works identically on Railway / Render / Fly.io / bare-metal
    // without any platform-specific dance. It's also the lightest engine
    // — adds ~2 MB to the fat jar.
    // -----------------------------------------------------------------
    implementation("io.ktor:ktor-client-core:3.4.3")
    implementation("io.ktor:ktor-client-cio:3.4.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.4.3")
    implementation("io.ktor:ktor-client-logging:3.4.3")
    implementation("io.ktor:ktor-client-auth:3.4.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.3")

    // -----------------------------------------------------------------
    // Jakarta Mail (formerly JavaMail) — pure-Java SMTP client used by
    // the SMTP email OTP provider. Works against any RFC-compliant
    // server: Gmail, Resend, SES, Mailgun, Postmark, Brevo (Sendinblue),
    // your own Postfix, etc. Zero hardcoding — host/port/credentials
    // all come from env vars.
    // -----------------------------------------------------------------
    implementation("org.eclipse.angus:jakarta.mail:2.0.3")

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.javaTime)
    implementation(libs.postgres)
    implementation(libs.hikaricp)
    implementation(libs.sqlite)
    implementation(libs.dotenv)

    // -----------------------------------------------------------------
    // Notification foundation (feature/setup_notification).
    //
    // Firebase Admin SDK is the ONLY sanctioned path for server-side FCM
    // dispatch in this codebase. We deliberately do NOT hand-roll POSTs to
    // the FCM REST endpoint — the Admin SDK handles service-account auth,
    // automatic token refresh, batched multicast, structured error responses
    // (UNREGISTERED → mark inactive), and retry/backoff for us.
    //
    // Initialisation is lazy and guarded (see feature/notification/firebase/
    // FirebaseAdminInitializer.kt): if FIREBASE_CREDENTIALS_* env vars are
    // absent, the NotificationService degrades to a no-op that logs a
    // warning instead of crashing the boot. This lets local dev (SQLite, no
    // Firebase project) run the rest of the API surface unchanged.
    // -----------------------------------------------------------------
    implementation(libs.firebase.admin)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}

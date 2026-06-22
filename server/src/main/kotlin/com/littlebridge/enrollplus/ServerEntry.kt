package com.littlebridge.enrollplus

/**
 * Inlined constants and helpers that used to live in the `:shared` (KMP) module.
 *
 * Why inlined?
 *   `:server` is a pure JVM Ktor backend. The `:shared` module is a Kotlin
 *   Multiplatform module that targets Android, iOS, JVM, JS, and wasmJs and
 *   pulls in AGP, Compose Multiplatform, Room + KSP, and Kotlin/Native — which
 *   makes a cold first-time build of `:server` extremely slow (and sometimes
 *   fail on low-spec dev machines) just to consume two trivial symbols.
 *
 *   Since `:server` only needed `SERVER_PORT` and a tiny `Greeting` helper from
 *   `:shared`, those two declarations are inlined here and the `:shared`
 *   dependency is dropped from `server/build.gradle.kts`. The `:server` module
 *   is now self-contained: `gradlew :server:run` no longer triggers the
 *   Android/iOS/JS/wasm toolchain downloads.
 */

/** Default HTTP port the Ktor server binds to when the `PORT` env var is not set. */
const val SERVER_PORT: Int = 8080

/**
 * Minimal server-side replacement for the KMP `Greeting` class.
 *
 * The original lived in `:shared/commonMain` and used an `expect/actual`
 * `getPlatform()` to print the JVM/iOS/Android name. On the server we always
 * run on the JVM, so we don't need the platform indirection.
 */
class Greeting {
    fun greet(): String = "Hello, JVM!"
}

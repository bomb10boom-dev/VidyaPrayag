import org.gradle.kotlin.dsl.implementation
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.google.services)
    kotlin("plugin.serialization")
}

// ---------------------------------------------------------------------------
// local.properties — devBaseUrl lets you point the dev flavor at your laptop
// (http://192.168.1.9:8080) while on the same WiFi as your phone.
// Falls back to render.com when the key is absent.
// ---------------------------------------------------------------------------
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val renderFallbackUrl = "https://vidyaprayag-1.onrender.com"
val devBaseUrl: String = localProps.getProperty("devBaseUrl")?.takeIf { it.isNotBlank() }
    ?: renderFallbackUrl

// Make the resolved dev URL visible at configuration time so you never have to
// guess which backend the phone is hitting.  If it fell back to render while
// you expected your laptop, this warning tells you `devBaseUrl` is missing.
if (devBaseUrl == renderFallbackUrl) {
    logger.warn(
        "[VidyaPrayag] devBaseUrl NOT set in local.properties -> dev flavor will " +
            "use $renderFallbackUrl. To point the phone at your laptop, add " +
            "`devBaseUrl=http://<laptop-LAN-ip>:8080` to local.properties."
    )
} else {
    logger.lifecycle("[VidyaPrayag] dev flavor baseUrl = $devBaseUrl")
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.ui.ExperimentalComposeUiApi")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    jvm()
    
    js {
        browser()
        binaries.executable()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    
    sourceSets {
        all {
            languageSettings.optIn("androidx.compose.ui.ExperimentalComposeUiApi")
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            // Provides ContextCompat for runtime location-permission checks
            // (real "use current location" in school onboarding, report §11.2).
            implementation(libs.androidx.core.ktx)
            // Native Android 12+ SplashScreen API (with compat back to API 24)
            // — zero white flash before the Compose content draws.
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)

            implementation(libs.firebase.messaging)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.compose)
            implementation("io.insert-koin:koin-compose-viewmodel:4.0.0")
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.okio)
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation(libs.androidx.navigation.compose)
            implementation(projects.shared)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinxCoroutinesSwing)
        }
    }
}

android {
    namespace = "com.littlebridge.enrollplus"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.littlebridge.enrollplus"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = "1.1"
    }
    
    buildFeatures {
        buildConfig = true
    }
    
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            buildConfigField("String", "AUTH_BASE_URL", "\"$devBaseUrl\"")
            buildConfigField("String", "SCHOOL_BASE_URL", "\"$devBaseUrl\"")
        }
        create("staging") {
            dimension = "environment"
            buildConfigField("String", "AUTH_BASE_URL", "\"https://vidyaprayag-1.onrender.com\"")
            buildConfigField("String", "SCHOOL_BASE_URL", "\"https://vidyaprayag-1.onrender.com\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "AUTH_BASE_URL", "\"https://vidyaprayag-1.onrender.com\"")
            buildConfigField("String", "SCHOOL_BASE_URL", "\"https://vidyaprayag-1.onrender.com\"")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

// ---------------------------------------------------------------------------
// Belt-and-braces fix for the "OLD landing keeps shipping" symptom (2026-06-08).
//
// Background: commit 78c33bf upgraded `CommonLandingScreenV2` and added bundled
// `landing_school_*.webp` drawables under `composeResources`. Despite the V2
// routing being verified correct end-to-end (NavGraphV2 → screen + Koin factory),
// fresh builds kept appearing to ship the OLD landing UI. The Gradle build cache
// (`org.gradle.caching=true`) was the loudest suspect (now disabled in
// `gradle.properties`, commit 22c9ed0), but it is NOT the only place stale
// Compose-Multiplatform resource accessors can come from:
//
//   1. The Gradle daemon's in-memory task cache can serve a stale
//      `generateComposeResClass` output across builds within the same daemon JVM.
//   2. AGP's intermediate-artifact cache can reuse `mergeResources` outputs
//      even when the upstream Compose resources have been regenerated.
//
// The block below forces both generator and the file-copy tasks the Compose
// Multiplatform plugin schedules under those names to run on EVERY build
// (`upToDateWhen { false }`). Task lookup is by name match so the wiring is
// resilient across CMP versions that have renamed the tasks (e.g. the
// `*ForCommonMain` vs `*ForAndroidMain` variants). This adds ~1–2s to a clean
// build but makes the landing-ship symptom impossible to reproduce.
//
// Pair with `./gradlew clean` ONCE after pulling this commit so any pre-existing
// stale outputs in `composeApp/build/generated/` are wiped; subsequent builds
// re-generate deterministically without manual intervention.
// ---------------------------------------------------------------------------
tasks.matching { task ->
    val n = task.name
    n.startsWith("generateComposeResClass") ||
        n.startsWith("generateResourceAccessorsForCommonMain") ||
        n.startsWith("generateResourceAccessorsFor") ||
        n.startsWith("copyNonXmlValueResourcesForCommonMain") ||
        n.startsWith("copyNonXmlValueResourcesFor") ||
        n.startsWith("prepareComposeResources")
}.configureEach {
    outputs.upToDateWhen { false }
}

compose.desktop {
    application {
        mainClass = "com.littlebridge.enrollplus.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.littlebridge.enrollplus"
            packageVersion = "1.0.0"
        }
    }
}

plugins {
    alias(libs.plugins.nowinandroid.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "es.saniexam.app"

    defaultConfig {
        applicationId = "es.saniexam.app"
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

// PR7 release-pipeline gate. Fails closed when the bundled pack
// manifest's `license` is in the refused set (dev-placeholder,
// unknown, blank, missing). Mirrors
// [es.saniexam.app.build.PackLicenseGate] at the Gradle layer so
// the gate is enforceable from CI without running the JUnit
// suite. Use `./gradlew :app:checkReleasePackLicense` from the
// project root.
tasks.register("checkReleasePackLicense") {
    group = "verification"
    description = "Refuses release builds that bundle a dev-placeholder / unknown / blank license."
    val manifest = file("src/main/assets/pack-manifest.json")
    doLast {
        require(manifest.exists()) { "pack-manifest.json missing at ${manifest.absolutePath}" }
        val raw = manifest.readText()
        val license = "\"license\"\\s*:\\s*\"([^\"]*)\""
            .toRegex()
            .find(raw)
            ?.groupValues
            ?.get(1)
            ?.trim()
            .orEmpty()
        val refused = setOf("dev-placeholder", "unknown")
        val isRefused = license.isEmpty() || license in refused
        if (isRefused) {
            val msg = buildString {
                appendLine("Refused: bundled pack manifest license is '$license'.")
                appendLine("Release pipeline gate (PR7) refuses to ship a public APK")
                appendLine("with a license in {dev-placeholder, unknown} or blank.")
                appendLine("Replace assets/question-packs/* and pack-manifest.json with a")
                appendLine("cleared-of-rights pack, or pin a known license (MIT / CC-BY-* / cleared-of-rights / Apache-2.0).")
            }
            throw GradleException(msg)
        }
        logger.lifecycle("checkReleasePackLicense: PASS (license='$license').")
    }
}

// Convenience alias that wires the gate into the standard
// `check` lifecycle so `./gradlew :app:check` fails the build
// when the bundled pack would be refused at release.
tasks.named("check") {
    dependsOn("checkReleasePackLicense")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.room.testing)
}

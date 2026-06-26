import groovy.json.JsonSlurper

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

// Release-pipeline gate. Fails closed when the bundled pack
// manifest's `license` is in the refused set (case-insensitive
// match against `dev-placeholder`, `unknown`, blank, missing) OR
// when the manifest is missing the required `category` field
// (spec `professional-categories` "Pack-Level Category Field").
// Implemented at the Gradle layer so the gate is enforceable from CI
// without running the JUnit suite. Use `./gradlew :app:checkReleasePackLicense`
// from the project root.
tasks.register("checkReleasePackLicense") {
    group = "verification"
    description = "Refuses release builds that bundle a dev-placeholder / unknown / blank license or a manifest without `category`."
    val manifest = file("src/main/assets/pack-manifest.json")
    doLast {
        require(manifest.exists()) { "pack-manifest.json missing at ${manifest.absolutePath}" }
        val parsed = JsonSlurper().parse(manifest) as? Map<*, *>
            ?: throw GradleException("Refused: pack-manifest.json root must be a JSON object.")
        val license = (parsed["license"] as? String)?.trim().orEmpty()
        val category = (parsed["category"] as? String)?.trim().orEmpty()
        val refused = setOf("dev-placeholder", "unknown")
        // Case-insensitive comparison so a case-variant like
        // `Dev-Placeholder` or `DEV-PLACEHOLDER` is still refused.
        val isLicenseRefused = license.isEmpty() || license.lowercase() in refused
        val isCategoryMissing = category.isEmpty()
        if (isLicenseRefused || isCategoryMissing) {
            val msg = buildString {
                appendLine("Refused: bundled pack manifest fails the release gate.")
                appendLine("  license = '$license' (refused=$isLicenseRefused)")
                appendLine("  category = '$category' (missing=$isCategoryMissing)")
                appendLine("Release pipeline gate refuses to ship a public APK when")
                appendLine("  - license is in {dev-placeholder, unknown} (any case), blank, or missing, OR")
                appendLine("  - the manifest has no `category` field (spec `professional-categories`).")
                appendLine("Replace assets/question-packs/* and pack-manifest.json with a")
                appendLine("cleared-of-rights pack carrying `category: \"TCAE\"`.")
            }
            throw GradleException(msg)
        }
        logger.lifecycle("checkReleasePackLicense: PASS (license='$license', category='$category').")
    }
}

// Convenience alias that wires the gate into the standard
// `check` lifecycle so `./gradlew :app:check` fails the build
// when the bundled pack would be refused at release.
tasks.named("check") {
    dependsOn("checkReleasePackLicense")
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
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

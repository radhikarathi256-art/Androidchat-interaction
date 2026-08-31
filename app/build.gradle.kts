import java.util.Properties

plugins {
    // AGP 9+ has built-in Kotlin support, so no kotlin.android plugin here.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Credentials come from local.properties, which .gitignore already excludes, so
// they stay off GitHub. A fresh clone has no values and falls back to empty
// strings — the app then builds but fails to connect, which is a far better
// failure than a key sitting in public source.
val localProps = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { load(it) }
}
fun secret(name: String): String = localProps.getProperty(name) ?: ""

android {
    namespace = "com.example.androidchat"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.androidchat"
        // 26+ so the animated IME inset APIs behave; keyboard/content sync is
        // best on API 30+, which is where the demo devices will be.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_HOST", "\"${secret("SUPABASE_HOST")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${secret("SUPABASE_KEY")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

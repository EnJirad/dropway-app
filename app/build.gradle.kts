import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Load Google Maps API Key from local.properties, system env, or project property
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val mapsApiKey: String = (localProps.getProperty("DROPWAY_GOOGLE_MAPS_KEY")
    ?: System.getenv("DROPWAY_GOOGLE_MAPS_KEY")
    ?: (project.findProperty("DROPWAY_GOOGLE_MAPS_KEY") as? String)
    ?: "YOUR_API_KEY_HERE").trim()

android {
    namespace = "com.pocketdev.dropway"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pocketdev.dropway"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["GOOGLE_MAPS_KEY"] = mapsApiKey
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"$mapsApiKey\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.activity:activity-compose:1.5.1")
    implementation("androidx.compose.ui:ui:1.3.0")
    implementation("androidx.compose.foundation:foundation:1.3.0")
    implementation("androidx.compose.material3:material3:1.0.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
}

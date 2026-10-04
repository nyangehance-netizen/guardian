plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.guardian.child"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.guardian.child"
        minSdk = 26          // Android 8.0 — required for UsageStats + reliable services
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Point this at your running backend. For an emulator talking to a
        // backend on your laptop, 10.0.2.2 is the host loopback.
        buildConfigField("String", "API_BASE", "\"http://10.0.2.2:4000\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { buildConfig = true; viewBinding = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")        // periodic policy sync
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    // No networking library needed — we use HttpURLConnection + org.json (built in).
}

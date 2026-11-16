plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "ar.godata.monitor"
    compileSdk = 37

    defaultConfig {
        // Same id as the old CreamUI build, so this installs over it.
        applicationId = "ar.godata.monitor"
        minSdk = 26
        // Not 37 yet: targeting Android 17 needs the runtime local-network permission to reach LAN servers.
        targetSdk = 36
        // Above the old cargo-apk build's code (16777472), or Android refuses the update as a downgrade.
        versionCode = 17000000
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    // UI components are Lumo UI's, generated into app/src/main/java/ar/godata/monitor/lumo/;
    // these are what they build on.
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-ripple")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    testImplementation("junit:junit:4.13.2")
}

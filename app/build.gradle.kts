// Build configuration for the app module
plugins {
    id("com.android.application")
}

android {
    compileSdk = 31
    defaultConfig {
        applicationId = "com.example.virtualcamera"
        minSdk = 21
        targetSdk = 31
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("androidx.core:core-ktx:1.6.0")
}
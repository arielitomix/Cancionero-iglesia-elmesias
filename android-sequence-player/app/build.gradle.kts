plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.arielalvarez.sequenceplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arielalvarez.sequenceplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.7"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_18
        targetCompatibility = JavaVersion.VERSION_18
    }

    kotlinOptions {
        jvmTarget = "18"
    }
}

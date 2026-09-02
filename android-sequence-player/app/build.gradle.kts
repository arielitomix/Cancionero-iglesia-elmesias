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
        versionCode = 18
        versionName = "0.17"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_18
        targetCompatibility = JavaVersion.VERSION_18
    }

    kotlinOptions {
        jvmTarget = "18"
    }
}

dependencies {
    implementation("com.github.neboyang:VoiceChanger:c1caf1224ab8c80d917b934771d16c2ef210bfba")
}

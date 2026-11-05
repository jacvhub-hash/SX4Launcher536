plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sx4.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sx4.launcher"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "4.0"
    }
    // ... resto de tu configuración
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.assistantbridge"
    compileSdk = 35
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "com.example.assistantbridge"
        minSdk = 23
        targetSdk = 31
        versionCode = 10
        versionName = "0.10"
    }
    signingConfigs {
        create("release") {
            val ks = System.getenv("SIGNING_KEYSTORE")
            if (!ks.isNullOrBlank()) {
                storeFile = file(ks)
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("release"); isMinifyEnabled = false } }
    lint {
        disable += "ExpiredTargetSdkVersion"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
}

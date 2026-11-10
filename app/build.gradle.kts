plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.folderonlymanager"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.folderonlymanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
    }
}
dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.documentfile:documentfile:1.0.1")
}

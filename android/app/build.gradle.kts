plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val packagedVersionCode = System.getenv("GJEJMA_VERSION_CODE")?.toIntOrNull() ?: 1
val packagedVersionName = System.getenv("GJEJMA_VERSION_NAME") ?: "1.0.0"

android {
    namespace = "al.gjejma.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "al.gjejma.app"
        minSdk = 26
        targetSdk = 36
        versionCode = packagedVersionCode
        versionName = packagedVersionName
    }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}



import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
fun buildConfigSecret(name: String) = "\"${localProps.getProperty(name, "").replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.libreta"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.libreta"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ANTHROPIC_KEY", buildConfigSecret("ANTHROPIC_KEY"))
        buildConfigField("String", "PICOVOICE_KEY", buildConfigSecret("PICOVOICE_KEY"))
        buildConfigField("String", "AI_PROVIDER", buildConfigSecret("AI_PROVIDER"))
        buildConfigField("String", "AI_BASE_URL", buildConfigSecret("AI_BASE_URL"))
        buildConfigField("String", "AI_API_KEY", buildConfigSecret("AI_API_KEY"))
        buildConfigField("String", "AI_MODEL", buildConfigSecret("AI_MODEL"))
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("ai.picovoice:porcupine-android:3.0.3")
    testImplementation("junit:junit:4.13.2")
}

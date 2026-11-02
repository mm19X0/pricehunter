plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val configuredApiBaseUrl = providers.gradleProperty("pricehunterApiBaseUrl")
    .orElse(providers.environmentVariable("PRICEHUNTER_API_BASE_URL"))
    .getOrElse("http://192.168.1.4:8000/")
val apiBaseUrl = if (configuredApiBaseUrl.endsWith("/")) {
    configuredApiBaseUrl
} else {
    "$configuredApiBaseUrl/"
}

android {
    namespace = "com.pricehunter.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pricehunter.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    val signingStoreFile = providers.environmentVariable("PRICEHUNTER_SIGNING_STORE_FILE").orNull
    val signingStorePassword = providers.environmentVariable("PRICEHUNTER_SIGNING_STORE_PASSWORD").orNull
    val signingKeyAlias = providers.environmentVariable("PRICEHUNTER_SIGNING_KEY_ALIAS").orNull
    val signingKeyPassword = providers.environmentVariable("PRICEHUNTER_SIGNING_KEY_PASSWORD").orNull
    if (signingStoreFile != null && signingStorePassword != null &&
        signingKeyAlias != null && signingKeyPassword != null
    ) {
        signingConfigs {
            create("release") {
                storeFile = file(signingStoreFile)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
        buildTypes {
            release {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
}

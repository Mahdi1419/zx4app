plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseSigningConfigured =
    providers.environmentVariable("KEYSTORE_BASE64").orNull?.isNotBlank() == true &&
    providers.environmentVariable("KEYSTORE_PASSWORD").orNull?.isNotBlank() == true &&
    providers.environmentVariable("KEY_ALIAS").orNull?.isNotBlank() == true &&
    providers.environmentVariable("KEY_PASSWORD").orNull?.isNotBlank() == true &&
    providers.environmentVariable("RELEASE_STORE_FILE").orNull?.isNotBlank() == true

android {
    namespace = "ir.smarthome.zx4"
    compileSdk = 34
    defaultConfig {
        applicationId = "ir.smarthome.zx4"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(providers.environmentVariable("RELEASE_STORE_FILE").get())
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
}

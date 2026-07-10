import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.navidabbasian.kibord"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.navidabbasian.kibord"
        minSdk = 21
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.4"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // امضای ریلیز از متغیرهای محیطی (CI)؛ در نبودشان بیلد ریلیز محلی با کلید دیباگ امضا می‌شود
        create("release") {
            val keystorePath = System.getenv("KIBORD_KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KIBORD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KIBORD_KEY_ALIAS")
                keyPassword = System.getenv("KIBORD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (System.getenv("KIBORD_KEYSTORE_PATH") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.splashscreen)
    testImplementation(libs.junit)
    debugImplementation(libs.compose.ui.tooling)
}

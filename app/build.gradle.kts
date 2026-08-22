import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

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
        versionCode = 22
        versionName = "1.2.0"

        // کلیدهای Supabase از local.properties خوانده می‌شوند تا در گیت نروند.
        // اگر نبودند، رشته‌ی خالی می‌ماند و اپ فقط آفلاین کار می‌کند.
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${System.getenv("SUPABASE_URL") ?: localProps.getProperty("SUPABASE_URL").orEmpty()}\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${System.getenv("SUPABASE_ANON_KEY") ?: localProps.getProperty("SUPABASE_ANON_KEY").orEmpty()}\"",
        )
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // امضای ریلیز: اول متغیرهای محیطی (CI)، بعد فایل keystore.properties در ریشه پروژه (بیلد محلی)
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
    }
    val hasReleaseKey = System.getenv("KIBORD_KEYSTORE_PATH") != null || keystorePropsFile.exists()

    signingConfigs {
        create("release") {
            val envPath = System.getenv("KIBORD_KEYSTORE_PATH")
            if (envPath != null) {
                storeFile = file(envPath)
                storePassword = System.getenv("KIBORD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KIBORD_KEY_ALIAS")
                keyPassword = System.getenv("KIBORD_KEY_PASSWORD")
            } else if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
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
            signingConfig = if (hasReleaseKey) {
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
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.splashscreen)
    testImplementation(libs.junit)
    debugImplementation(libs.compose.ui.tooling)
}

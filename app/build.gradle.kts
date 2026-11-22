@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dawidggs.aliashub"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dawidggs.aliashub"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"
    }

    // Release signing is configured from environment variables (set by CI from
    // GitHub secrets). If they're absent, the release build is left unsigned so
    // local/debug builds keep working.
    val keystorePath: String? = System.getenv("KEYSTORE_FILE")
    val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
    val keyAliasEnv: String? = System.getenv("KEY_ALIAS")
    val keyPasswordEnv: String? = System.getenv("KEY_PASSWORD")
    val hasSigning = !keystorePath.isNullOrBlank() &&
        file(keystorePath).exists() &&
        !keystorePassword.isNullOrBlank() &&
        !keyAliasEnv.isNullOrBlank() &&
        !keyPasswordEnv.isNullOrBlank()

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        generateLocaleConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.material.icons.core)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.errorprone.annotations)

    implementation(libs.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)

    debugImplementation(libs.ui.tooling)
}

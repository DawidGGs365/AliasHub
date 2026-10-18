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
        versionCode = 5
        versionName = "0.9.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

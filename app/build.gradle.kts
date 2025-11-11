@file:Suppress("UnstableApiUsage")

import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
}

setupApp()

android {
    namespace = "io.vvpn.android"
    
    defaultConfig {
        applicationId = "io.vvpn.android"
        versionCode = 3
        versionName = "1.0.0"
        
        splits.abi {
            reset()
            include(
                "arm64-v8a",
                "armeabi-v7a",
                "x86_64",
                "x86"
            )
        }
        ndkVersion = "28.2.13676358"
    }
    
    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }
    
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    
    ksp {
        arg("room.incremental", "true")
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    
    bundle {
        language {
            enableSplit = false
        }
    }
    
    buildFeatures {
        viewBinding = true
        aidl = true
        buildConfig = true
    }
    
    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

dependencies {
    implementation(fileTree("libs"))
    
    // Kotlin & Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    implementation("androidx.browser:browser:1.9.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.3")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    implementation("androidx.work:work-multiprocess:2.10.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    
    // Material Design
    implementation("com.google.android.material:material:1.12.0")
    
    // JSON
    implementation("com.google.code.gson:gson:2.13.1")
    
    // Editor
    implementation("com.blacksquircle.ui:editorkit:2.9.0")
    implementation("com.blacksquircle.ui:language-base:2.9.0")
    implementation("com.blacksquircle.ui:language-json:2.9.0")
    
    // Camera
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    
    // Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    
    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // QR Code
    implementation("com.google.zxing:core:3.5.3")
    
    // Utilities
    implementation("com.jakewharton:process-phoenix:3.0.0")
    implementation("com.esotericsoftware:kryo:5.6.2")
    implementation("org.ini4j:ini4j:0.5.4")
    implementation("com.twofortyfouram:android-plugin-api-for-locale:1.0.4")
    
    // Smali & Guava
    implementation("com.android.tools.smali:smali-dexlib2:3.0.9") {
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation("com.google.guava:guava:33.4.8-android")
    
    // Room Database
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    
    // Room Migration
    implementation("com.github.MatrixDev.Roomigrant:RoomigrantLib:0.3.4")
    ksp("com.github.MatrixDev.Roomigrant:RoomigrantCompiler:0.3.4")
    
    // Preference
    implementation("dev.rikka.rikkax.preference:simplemenu-preference:1.0.3")
    
    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    
    // Testing
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

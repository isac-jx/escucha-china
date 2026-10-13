plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.isacjx.escuchachina"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.isacjx.escuchachina"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // El M52 es arm64. Incluimos también armeabi-v7a por compatibilidad.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    // El .aar de sherpa-onnx trae .so grandes; no comprimir los modelos onnx si se empaquetaran.
    androidResources {
        noCompress += listOf("onnx")
    }
}

dependencies {
    // --- Reconocimiento de voz offline (sherpa-onnx) ---
    // El workflow de GitHub descarga este .aar a app/libs antes de compilar.
    // El .aar es auto-contenido (incluye onnxruntime), no se agrega aparte.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // --- Traducción offline (ML Kit) ---
    implementation("com.google.mlkit:translate:17.0.3")

    // --- AndroidX / UI ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // El historial se guarda con org.json (incluido en Android), sin dependencias extra.
}

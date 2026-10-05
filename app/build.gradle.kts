plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cl.lossless.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "cl.lossless.player"
        minSdk = 34          // Android 14: necesario para AudioMixerAttributes (bit-perfect USB)
        targetSdk = 36
        versionCode = 2
        versionName = "1.1-exp"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }

val media3 = "1.8.0"   // revisa la última versión estable de Media3

dependencies {
    // Reproducción y sesión multimedia (Google / AndroidX)
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")

    // Decodificador FFmpeg: respaldo para ALAC (y FLAC/MP3) si el dispositivo no tiene
    // decodificador nativo. Google no publica este módulo en Maven; esta es la
    // compilación que mantiene Jellyfin. Su versión debe coincidir con la de Media3.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.8.0+1")

    // UI
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")

    // Carga de imágenes (carátulas locales y vistas previas de internet)
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
}

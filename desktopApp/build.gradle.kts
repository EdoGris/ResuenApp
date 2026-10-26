import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// Versión de escritorio (Windows). Reutiliza la lógica del módulo :shared (letras, LRCLIB,
// detección de texto) y trae su propio motor de audio: FFmpeg para decodificar y Java Sound
// para la salida (modo compartido de Windows; el bit-perfect con WASAPI exclusivo viene después).
kotlin { jvmToolchain(17) }

val javacpp = "1.5.11"

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // En Android org.json viene con el sistema; aquí hay que traerlo (lo usa LrcLibClient).
    implementation("org.json:json:20240303")

    // Etiquetas, duración, formato y carátulas incrustadas (FLAC, MP3, M4A/ALAC, WAV, OGG).
    implementation("net.jthink:jaudiotagger:3.0.1")

    // Decodificación con FFmpeg. Solo el lector de javacv y las librerías de FFmpeg para
    // Windows de 64 bits (sin OpenCV ni los demás módulos que javacv arrastra).
    implementation("org.bytedeco:javacv:$javacpp") { isTransitive = false }
    implementation("org.bytedeco:javacpp:$javacpp")
    implementation("org.bytedeco:ffmpeg:7.1-$javacpp")
    runtimeOnly("org.bytedeco:javacpp:$javacpp:windows-x86_64")
    runtimeOnly("org.bytedeco:ffmpeg:7.1-$javacpp:windows-x86_64")

    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "cl.lossless.player.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "ResuenApp"
            packageVersion = "1.1.0"
            description = "Reproductor de música sin pérdida"
            vendor = "EdoGris"
            includeAllModules = true

            windows {
                menu = true
                menuGroup = "ResuenApp"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Mismo ícono que la app Android (icons/resuenapp.svg es la fuente).
                iconFile.set(project.file("icons/resuenapp.ico"))
                // Fijo: permite que una versión nueva del .msi reemplace a la anterior.
                upgradeUuid = "6f1c2a8e-3b4d-4c5e-9f60-7a8b9c0d1e2f"
            }
        }
    }
}

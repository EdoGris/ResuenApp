plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

// Lógica sin dependencias de Android (letras, carátulas, ecualizador, detección de voz),
// compartida entre la app Android y la futura versión de escritorio (Windows).
// Por ahora solo hay destino JVM: Android lo consume igual que una librería Kotlin/JVM.
// Cuando llegue iOS, lo que no use java.* pasa a commonMain.
kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            // En Android org.json viene en el sistema; en escritorio lo aporta cada app.
            compileOnly("org.json:json:20240303")
        }
        jvmTest.dependencies {
            implementation("junit:junit:4.13.2")
            implementation("org.json:json:20240303")
        }
    }
}

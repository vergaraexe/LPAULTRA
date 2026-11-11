plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cl.lpa.agenda"
    compileSdk = 34

    defaultConfig {
        applicationId = "cl.lpa.agenda"
        minSdk = 26
        targetSdk = 34
        versionCode = 11
        versionName = "1.6"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    // Firma fija: todas las versiones nuevas se instalan SOBRE la anterior sin perder lo escrito
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/LICENSE.txt",
                "META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/*.md"
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Envio semanal en segundo plano
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Correo (SMTP)
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    // Reconocimiento de escritura a mano (descripcion de fotos)
    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")
    // Guardar la clave del correo cifrada
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Orientacion de las fotos
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.dompet.keuangan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dompet.keuangan"
        minSdk = 26
        targetSdk = 35
        // Naik otomatis di tiap build GitHub Actions supaya APK baru selalu bisa menimpa yang lama
        versionCode = 3 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
        versionName = "1.6"
    }
    // Kunci tanda tangan tetap: update APK menimpa aplikasi lama tanpa menghapus data
    signingConfigs {
        create("dompet") {
            storeFile = file("dompet.keystore")
            storePassword = "dompet123"
            keyAlias = "dompet"
            keyPassword = "dompet123"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("dompet")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

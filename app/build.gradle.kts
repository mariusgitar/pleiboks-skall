plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Adressen skallet åpner. Kan overstyres ved bygging: gradle assembleRelease -PpleiboksUrl=https://…
val pleiboksUrl = (project.findProperty("pleiboksUrl") as String?)?.takeIf { it.isNotBlank() }
    ?: "https://pleiboks.vercel.app/"
val byggNr = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val nokkel = System.getenv("KEYSTORE_PATH")

android {
    namespace = "no.pleiboks.skall"
    compileSdk = 34

    defaultConfig {
        applicationId = "no.pleiboks.skall"
        minSdk = 26          // Galaxy S7 (Android 8.0)
        targetSdk = 34
        versionCode = byggNr
        versionName = "1.0.$byggNr"
        buildConfigField("String", "PLEIBOKS_URL", "\"$pleiboksUrl\"")
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        create("boks") {
            if (nokkel != null) {
                storeFile = file(nokkel)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Samme nøkkel hver gang, ellers kan ikke nye versjoner installeres over den gamle
            signingConfig = if (nokkel != null) signingConfigs.getByName("boks") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

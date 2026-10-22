plugins {
    id("com.android.application")
}

android {
    namespace = "com.sampwrap.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sampwrap.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 193
        versionName = "1.9.3"

        // Konfigurasi branding & hosting ada di assets/app_config.properties
    }

    buildFeatures {
        buildConfig = true
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

    // tanpa dependency eksternal: build cepat & tahan perubahan
    packaging {
        resources.excludes += setOf("META-INF/*")
    }
}

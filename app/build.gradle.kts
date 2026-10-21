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
        versionCode = 1
        versionName = "1.0.0"

        // Nilai ini bisa diubah dari gradle.properties tanpa nyentuh kode
        val appName = (project.findProperty("sampwrap.appName") as String?) ?: "SAMP PC"
        val server = (project.findProperty("sampwrap.server") as String?) ?: "127.0.0.1:7777"
        val baseUrl = (project.findProperty("sampwrap.packBaseUrl") as String?) ?: ""
        val archiveUrl = (project.findProperty("sampwrap.packArchiveUrl") as String?) ?: ""

        buildConfigField("String", "APP_LABEL", "\"$appName\"")
        buildConfigField("String", "DEFAULT_SERVER", "\"$server\"")
        buildConfigField("String", "PACK_BASE_URL", "\"$baseUrl\"")
        buildConfigField("String", "PACK_ARCHIVE_URL", "\"$archiveUrl\"")
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

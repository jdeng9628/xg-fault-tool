plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.xg.faulttool"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xg.faulttool"
        minSdk = 24
        targetSdk = 34
        versionCode = project.findProperty("appVersionCode")?.toString()?.toInt() ?: 1
        versionName = project.findProperty("appVersionName")?.toString() ?: "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(project.findProperty("keystoreFile")?.toString() ?: "xg-fault-tool.keystore")
            storePassword = project.findProperty("keystorePassword")?.toString() ?: ""
            keyAlias = project.findProperty("keyAlias")?.toString() ?: ""
            keyPassword = project.findProperty("keyPassword")?.toString() ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.webkit:webkit:1.8.0")
}

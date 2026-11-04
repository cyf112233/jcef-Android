plugins {
    id("com.android.application")
    id("com.cefrium") version "0.17.0"
}

android {
    namespace = "com.mcef.host"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mcef.host"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        // Cefrium 只出 arm64-v8a 和 x86_64
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 引擎在多个子进程里按类名反射加载，混淆会直接炸
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].jniLibs.srcDir("src/main/jniLibs")

    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation("com.cefrium:cefrium-sdk:0.17.0")
    // Cefrium 运行时要的（官方 quickstart 明确列出）
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.browser:browser:1.8.0")
}

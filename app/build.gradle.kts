import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.contax.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.contax.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    // ── نام‌گذاری خودکار فایل APK با نام و نسخه برنامه ──
    // خروجی: Contax-v{versionName}.apk (release) یا Contax-v{versionName}-debug.apk (debug)
    applicationVariants.all {
        // نام واریانت به‌صورت رشته؛ در اسکوپ بیرونی می‌گیریم تا مقایسه با String درست کار کند
        val variantName = name
        outputs.all {
            val output = this as BaseVariantOutputImpl
            val suffix = if (variantName == "release") "" else "-$variantName"
            output.outputFileName = "Contax-v${versionName}${suffix}.apk"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Room (backup history)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager (scheduled backups)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // DataStore (settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Biometric (app lock)
    implementation("androidx.biometric:biometric:1.1.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

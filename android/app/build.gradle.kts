plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.mangavault"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.mangavault"
        minSdk = 26
        targetSdk = 34
        // Each CI build gets a higher number so it installs as an update over the previous one.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 100 + build
        versionName = "4.0.$build"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One fixed key for every build: Android only installs an update when the signature matches the installed app.
    // It lives in the repo because the APK is side-loaded; use a private key (kept out of git) to publish on a store.
    signingConfigs {
        create("vault") {
            storeFile = file("mangavault.keystore")
            storePassword = "mangavault"
            keyAlias = "mangavault"
            keyPassword = "mangavault"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("vault")
        }
        debug {
            signingConfig = signingConfigs.getByName("vault")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("io.coil-kt:coil-compose:2.6.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

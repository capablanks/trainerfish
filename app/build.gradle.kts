plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // Kotlin 2.x Compose plugin
}

android {
    namespace = "com.tonorbe.trainerfish"
    compileSdk = 36

    // Pin to the exact NDK you installed (SDK Manager → Show Package Details)
    ndkVersion = "27.0.12077973"   // ← change if your NDK folder differs

    defaultConfig {
        applicationId = "com.tonorbe.trainerfish"
        minSdk = 24
        targetSdk = 35
        versionCode = 500
        versionName = "5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Build the JNI .so for these ABIs (add "armeabi-v7a" if you want 32-bit too)
        ndk {
            abiFilters += "arm64-v8a"
            abiFilters += "armeabi-v7a"
            abiFilters += "x86_64"     // ✅ emulator + Play “official emulator”
            // abiFilters += "x86"     // optional (older emulators)
        }

        // Pass C++ flags to CMake
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3", "-DNDEBUG")
            }
        }

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Point Gradle at your CMakeLists.txt
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    // Packaging options for native libs
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildFeatures {
        compose = true
    }

    // Java 17 to match Kotlin toolchain
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Keep these types uncompressed in APK
    androidResources {
        noCompress += listOf("pgn", "json")
    }

    buildTypes {
        getByName("release") {
            // ✅ Kotlin DSL properties
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false

            // ✅ Use your release signing config
            //signingConfig = signingConfigs.getByName("release")

            // ✅ ProGuard/R8 config
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.material3)
    implementation(libs.litert)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.unit)

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    testImplementation("junit:junit:4.13.2")

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("com.github.bhlangonijr:chesslib:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-svg:2.6.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.android.billingclient:billing-ktx:7.0.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("androidx.compose.material:material-icons-extended")

}

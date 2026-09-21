plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.warrantyvault"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.warrantyvault"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = false
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE,LICENSE.txt,NOTICE,NOTICE.txt}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

// java.time is desugared to all API levels by the D8 desugarer (coreLibraryDesugaring below),
// so NewApi lint errors for java.time on minSdk 24 are false positives.
android {
    lint {
        disable += "NewApi"
    }
}

dependencies {
    // java.time desugaring for minSdk 24 (enables LocalDate etc. on API 24/25)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Core Android
    val core_ktx_version = "1.13.1"
    val appcompat_version = "1.7.0"
    val activity_version = "1.9.0"
    val fragment_version = "1.8.2"
    val constraintlayout_compose_version = "1.0.1"
    val material_version = "1.12.0"

    implementation("androidx.core:core-ktx:$core_ktx_version")
    implementation("androidx.appcompat:appcompat:$appcompat_version")
    implementation("androidx.activity:activity-compose:$activity_version")
    implementation("androidx.fragment:fragment-ktx:$fragment_version")
    implementation("androidx.constraintlayout:constraintlayout-compose:$constraintlayout_compose_version")
    implementation("com.google.android.material:material:$material_version")

    // Compose BOM
    val compose_bom_version = "2024.06.00"
    implementation(platform("androidx.compose:compose-bom:$compose_bom_version"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.runtime:runtime-rxjava3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room Database
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Coroutines & Flow
    val coroutines_version = "1.7.3"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Serialization
    val serialization_version = "1.6.2"
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")

    // Coil for image loading
    val coil_version = "2.6.0"
    implementation("io.coil-kt:coil-compose:$coil_version")

    // CameraX for real document capture
    val camerax_version = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    // Lifecycle ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Material Icons Extended
        implementation("androidx.compose.material:material-icons-extended")
    
        // Android OCR - ML Kit Text Recognition
        implementation("com.google.mlkit:text-recognition:16.0.1")
    
        // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
}
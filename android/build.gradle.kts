plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    // Reads android/google-services.json (not in git: CI writes it from the GOOGLE_SERVICES_JSON secret).
    id("com.google.gms.google-services")
}

android {
    namespace = "com.imad.planner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.imad.planner"
        minSdk = 26
        targetSdk = 35
        versionCode = 38
        versionName = "0.37"
    }

    // Release APKs come out unsigned here and are signed by scripts/sign.sh in CI with the private key
    // (kept encrypted in signing/, opened by a repository secret). Debug builds use Android's local debug key.
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }

    // The shared logic (../shared) is compiled as part of the phone app, not as a library: Kotlin does not smart-cast
    // another module's public properties, and the phone code relies on that. The same files, unchanged, also build
    // on their own as :shared (with its own tests) and will be used by the computer app.
    sourceSets {
        getByName("main").java.srcDir("../shared/src/main/kotlin")
        // The theme and drawings, shared with the computer app.
        getByName("main").java.srcDir("../shared/src/compose/kotlin")
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation("androidx.room:room-common:2.6.1")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Sync with the computer: Google sign-in and Firestore.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

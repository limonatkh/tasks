import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // Only the @Entity / @PrimaryKey annotations, so the phone's Room database keeps using these classes as they are.
    api("androidx.room:room-common:2.6.1")
    // Android has org.json built in; the computer app adds it itself. Bundling it into the APK would clash.
    compileOnly("org.json:json:20240303")
    // Flow, Mutex and coroutines for the Repository (the phone already has them).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

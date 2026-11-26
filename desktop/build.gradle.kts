import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Windows app. It compiles the shared logic and the shared theme/drawings from ../shared as its own sources
// (like the phone does), and packs the phone's seed files and fonts as resources.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// firebase.properties (project id and the app's public web key) is made from android/google-services.json, which is
// not in git; CI writes it from the GOOGLE_SERVICES_JSON secret. Without it the app works but cannot sync.
val firebaseProps by tasks.registering {
    val src = rootProject.file("android/google-services.json")
    val out = layout.buildDirectory.dir("generated/firebase")
    inputs.property("config", providers.provider { if (src.exists()) src.readText() else "" })
    outputs.dir(out)
    doLast {
        val text = if (src.exists()) src.readText() else ""
        fun pick(name: String) = Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1) ?: ""
        val dir = out.get().asFile.apply { mkdirs() }
        File(dir, "firebase.properties").writeText("apiKey=${pick("current_key")}\nprojectId=${pick("project_id")}\n")
    }
}

sourceSets {
    main {
        resources.srcDir(files(layout.buildDirectory.dir("generated/firebase")).builtBy(firebaseProps))
        kotlin.srcDir("../shared/src/main/kotlin")
        kotlin.srcDir("../shared/src/compose/kotlin")
        resources.srcDir("../android/src/main/assets")
        resources.srcDir("../android/src/main/res/font")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("androidx.room:room-common:2.6.1")
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

compose.desktop {
    application {
        mainClass = "com.imad.planner.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "SmartLifePlanner"
            packageVersion = "1.0.0"
            description = "رحلة اليوم"
            vendor = "Smart Life Planner"
            windows {
                // Keeps installs of later versions as updates of this one.
                upgradeUuid = "6f1c2b7e-3d4a-4f5e-9b8c-1a2d3e4f5a6b"
                menu = true
                shortcut = true
                perUserInstall = true
                menuGroup = "رحلة اليوم"
            }
        }
    }
}

// Pictures of every screen, drawn without a window (Shots.kt), for checking in CI: desktop/build/shots/*.png
tasks.register<JavaExec>("shots") {
    group = "verification"
    mainClass.set("com.imad.planner.ShotsKt")
    classpath = sourceSets["main"].runtimeClasspath
    args(layout.buildDirectory.dir("shots").get().asFile.absolutePath)
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "SmartLifePlanner"

// The phone app. Its Gradle name stays ":app" so the APK keeps its name (app-release.apk); the folder is android/.
include(":app")
project(":app").projectDir = file("android")

// Logic shared by the phone and the computer: models, scheduling, prayer times, plans, progress. Plain Kotlin, no Android.
include(":shared")

// The Windows app (Compose Desktop). It compiles shared/ the same way the phone does.
include(":desktop")

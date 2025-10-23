pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven("https://jitpack.io")
        gradlePluginPortal()
    }

    plugins {
        // ✅ Core Android + Kotlin
        id("com.android.application") version "8.1.0"
        id("org.jetbrains.kotlin.android") version "1.9.0"

        // ✅ Navigation Safe Args
        id("androidx.navigation.safeargs.kotlin") version "2.7.5"

        // ✅ Firebase / Google Services (kept newer 4.4.3 version)
        id("com.google.gms.google-services") version "4.4.3"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "COS30049"
include(":app")

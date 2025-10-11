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
        // ✅ Add this line for the Android Application plugin
        id("com.android.application") version "8.1.0"

        // ✅ Add this line for the Kotlin Android plugin
        id("org.jetbrains.kotlin.android") version "1.9.0"

        // ✅ Add this line for Navigation Safe Args
        id("androidx.navigation.safeargs.kotlin") version "2.7.5"

        id("com.google.gms.google-services") version "4.4.3" // ✅ Firebase plugin added here
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

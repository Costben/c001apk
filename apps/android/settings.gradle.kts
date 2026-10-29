@file:Suppress("UnstableApiUsage")

rootProject.name = "c001apk"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// coui17 is consumed from source: its io.github.suqi8.coui.kmp modules replace the
// published coordinates. A `coui` checkout next to this repository (the fork being worked on locally)
// wins over the submodule, so local builds follow its latest state while CI stays on the submodule's
// pinned commit.
val siblingCoui = rootDir.resolve("../../../coui")
includeBuild(
    if (siblingCoui.resolve("settings.gradle.kts").isFile) siblingCoui
    else rootDir.resolve("../../third_party/coui17"),
)

include(":app")

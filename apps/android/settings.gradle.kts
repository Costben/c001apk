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
        // COUI 17：本机存在 ../../../coui/build/repository/local 且未强制走远端时优先用本地最新快照；
        // 外部环境或 CI 自动回退到 Costben/coui17 的 maven-repo 孤儿分支。
        val localCouiRepo = rootDir.resolve("../../../coui/build/repository/local")
        val forceRemoteCoui = providers.gradleProperty("coui.useRemote").orNull.equals("true", ignoreCase = true)
        exclusiveContent {
            forRepository {
                maven {
                    if (localCouiRepo.isDirectory && !forceRemoteCoui) {
                        name = "localCouiRepo"
                        url = uri(localCouiRepo)
                    } else {
                        name = "coui17MavenRepo"
                        url = uri("https://raw.githubusercontent.com/Costben/coui17/maven-repo/coui-repo")
                    }
                }
            }
            filter {
                includeGroup("io.github.suqi8.coui.kmp")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":app")

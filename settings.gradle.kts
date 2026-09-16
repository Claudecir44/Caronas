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
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Só pro ShortcutBadger (badge numérico no ícone do launcher,
        // ver AppIconBadgeUtil.kt) — não publicado no Maven Central, só
        // aqui.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Caronas"
include(":app")
 
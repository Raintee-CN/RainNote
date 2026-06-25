pluginManagement {
    repositories {
        maven("http://127.0.0.1:30080/maven/google/") {
            isAllowInsecureProtocol = true
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("http://127.0.0.1:30080/maven/central/") {
            isAllowInsecureProtocol = true
        }
        maven("http://127.0.0.1:30080/maven/gradle-plugin/") {
            isAllowInsecureProtocol = true
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("http://127.0.0.1:30080/maven/google/") {
            isAllowInsecureProtocol = true
        }
        maven("http://127.0.0.1:30080/maven/central/") {
            isAllowInsecureProtocol = true
        }
    }
}

rootProject.name = "RainNote"
include(":app")

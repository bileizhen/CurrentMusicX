pluginManagement {
    buildscript {
        repositories {
            val officialFirst = providers.gradleProperty("lei.officialRepositoriesFirst").orNull == "true"
            if (officialFirst) { google(); mavenCentral() }
            maven("https://maven.aliyun.com/repository/google")
            if (!officialFirst) { google(); mavenCentral() }
        }
        dependencies {
            // Kotlin 2.4 requires R8 >= 9.1.29; AGP 8.13.2 embeds R8 8.13.19.
            // Remove this override when AGP bundles a compatible version.
            // https://developer.android.com/build/kotlin-support
            classpath("com.android.tools:r8:9.1.57")
        }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                // Avoid mirror errors when resolving the KSP plugin marker artifact.
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        val officialFirst = providers.gradleProperty("lei.officialRepositoriesFirst").orNull == "true"
        if (officialFirst) {
            google(); mavenCentral(); gradlePluginPortal()
        }
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        if (!officialFirst) {
            google(); mavenCentral(); gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val officialFirst = providers.gradleProperty("lei.officialRepositoriesFirst").orNull == "true"
        if (officialFirst) { google(); mavenCentral() }
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        if (!officialFirst) { google(); mavenCentral() }
    }
}
rootProject.name = "CurrentMusic"
include(":app")
// Local performance tools are not distributed with the repository.
if (file("baselineprofile/build.gradle.kts").isFile && file("benchmark/build.gradle.kts").isFile) {
    include(":baselineprofile", ":benchmark")
}

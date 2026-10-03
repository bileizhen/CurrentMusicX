pluginManagement {
    repositories {
        val officialFirst = providers.gradleProperty("lei.officialRepositoriesFirst").orNull == "true"
        if (officialFirst) {
            google(); mavenCentral(); gradlePluginPortal()
        }
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
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

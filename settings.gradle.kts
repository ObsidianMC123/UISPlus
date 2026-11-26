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
        // v1.7: NewPipeExtractor (nhac YouTube) chi co tren JitPack
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "UISApp"
include(":app")

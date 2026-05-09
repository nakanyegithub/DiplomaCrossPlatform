pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Автовыбор / загрузка JDK под toolchain (устраняет javaCompiler в AGP при «кривом» Gradle JDK в Studio)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DiplomaCrossPlatform"
include(":composeApp")
include(":server")

pluginManagement {
    plugins {
        id("com.vanniktech.maven.publish") version "0.30.0"
    }

    includeBuild("autumn-gradle-plugin")
    includeBuild("autumn-certifier")
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}

rootProject.name = "autumn"

include(
    "autumn-core",
    "autumn-state",
    "autumn-buckets",
    "autumn-observatory",
    "autumn-resolver",
    "autumn-config",
    "autumn-ui",
    "autumn-compiler-plugin",
    "autumn-sandbox",
    "autumn-demo",
    "autumn-benchmarks",
    "autumn-ide-plugin"
)

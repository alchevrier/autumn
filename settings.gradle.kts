pluginManagement {
    plugins {
        id("com.vanniktech.maven.publish") version "0.30.0"
    }

    includeBuild("autumn-gradle-plugin")
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

rootProject.name = "autumn"

include(
    "autumn-core",
    "autumn-state",
    "autumn-buckets",
    "autumn-resolver",
    "autumn-config",
    "autumn-ui",
    "autumn-compiler-plugin",
    "autumn-sandbox",
    "autumn-demo"
)

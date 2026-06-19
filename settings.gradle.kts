pluginManagement {
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
    "autumn-gradle-plugin",
    "autumn-sandbox"
)

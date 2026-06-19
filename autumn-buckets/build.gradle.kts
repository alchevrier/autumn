plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("dev.autumn.plugin") version "1.0.0-SNAPSHOT"
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":autumn-core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}


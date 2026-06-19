plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("dev.autumn.plugin") version "1.0.2"
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":autumn-core"))
            implementation(project(":autumn-state"))
            implementation(project(":autumn-config"))
            implementation(project(":autumn-resolver"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
        }
    }
}

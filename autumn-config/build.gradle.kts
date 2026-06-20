plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("dev.autumn.plugin") version "1.0.2"
}

kotlin {
    jvm()
    js(IR) { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":autumn-core"))
            implementation(project(":autumn-resolver"))
            implementation(project(":autumn-buckets"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

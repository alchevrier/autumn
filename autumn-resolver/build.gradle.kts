plugins {
    id("org.jetbrains.kotlin.multiplatform")
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

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":autumn-core"))
            implementation(project(":autumn-state"))
        }
        commonTest.dependencies {}
    }
}

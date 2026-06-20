plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()
    js(IR) { nodejs() }

    sourceSets {
        commonMain.dependencies {}
        commonTest.dependencies {}
    }
}

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()
    js { browser() }

    sourceSets {
        commonMain.dependencies {}
        commonTest.dependencies {}
    }
}

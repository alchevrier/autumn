plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("dev.autumn.plugin") version "1.0.2"
}

kotlin {
    jvm()
    wasmJs { 
        browser { 
            testTask { enabled = false }
        }
    }
    js {
        nodejs()
    }
    
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":autumn-core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

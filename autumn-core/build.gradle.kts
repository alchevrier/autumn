plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()
    js(IR) { nodejs() }
    
    linuxX64 {
        compilations.getByName("main") {
            cinterops {
                val autumn_xdp by creating {
                    defFile(project.file("src/nativeInterop/cinterop/autumn_xdp.def"))
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {}
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
        }
    }
}

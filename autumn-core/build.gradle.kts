plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()
    js(IR) { nodejs() }
    wasmJs { browser() }
    
    linuxX64 {
        compilations.getByName("main") {
            cinterops {
                val autumn_xdp by creating {
                    defFile(project.file("src/nativeInterop/cinterop/autumn_xdp.def"))
                    compilerOpts("-Isrc/nativeInterop/cinterop")
                }
                val autumn_os by creating {
                    defFile(project.file("src/nativeInterop/cinterop/autumn_os.def"))
                    compilerOpts("-Isrc/nativeInterop/cinterop")
                }
            }
        }
    }
    
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.ExperimentalMultiplatform")
        }
        commonMain.dependencies {}
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
        }
        val jvmTest by getting {
            dependencies {
                implementation("commons-codec:commons-codec:1.16.0")
            }
        }
    }
}
tasks.register<Exec>("compileAutumnXdp") {
    val srcDir = file("src/nativeInterop/cinterop")
    val cFile = srcDir.resolve("autumn_xdp.c")
    val oFile = srcDir.resolve("autumn_xdp.o")
    val aFile = srcDir.resolve("autumn_xdp.a")

    inputs.file(cFile)
    outputs.file(aFile)

    commandLine("bash", "-c", "gcc -c -fPIC ${cFile.absolutePath} -o ${oFile.absolutePath} && ar rcs ${aFile.absolutePath} ${oFile.absolutePath}")
}

tasks.register<Exec>("compileAutumnOs") {
    val srcDir = file("src/nativeInterop/cinterop")
    val cFile = srcDir.resolve("autumn_os.c")
    val oFile = srcDir.resolve("autumn_os.o")
    val aFile = srcDir.resolve("autumn_os.a")

    inputs.file(cFile)
    outputs.file(aFile)

    commandLine("bash", "-c", "gcc -c -fPIC ${cFile.absolutePath} -o ${oFile.absolutePath} && ar rcs ${aFile.absolutePath} ${oFile.absolutePath}")
}

// Make sure that cinterop waits for the C compilation to finish
tasks.matching { it.name.startsWith("cinteropAutumn_xdp") }.configureEach {
    dependsOn("compileAutumnXdp")
}
tasks.matching { it.name.startsWith("cinteropAutumn_os") }.configureEach {
    dependsOn("compileAutumnOs")
}

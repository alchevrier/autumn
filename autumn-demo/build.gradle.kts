plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("dev.autumn.plugin") version "1.0.2"
}

kotlin {
    jvm {
    }
    wasmJs {
        browser {
            binaries.executable()
        }
    }
    js(IR) {
        browser {
            binaries.executable()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":autumn-core"))
                implementation(project(":autumn-state"))
                implementation(project(":autumn-buckets"))
                implementation(project(":autumn-config"))
                implementation(project(":autumn-resolver"))
                implementation(project(":autumn-ui"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-server-core:3.0.1")
                implementation("io.ktor:ktor-server-netty:3.0.1")
                implementation("io.ktor:ktor-server-cors:3.0.1")
                implementation("org.slf4j:slf4j-simple:2.0.13")
            }
        }
        val jsMain by getting {
            dependencies {
            }
        }
    }
}

val runDemoServer by tasks.registering(JavaExec::class) {
    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main") as org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
    classpath = jvmMain.output.allOutputs + jvmMain.runtimeDependencyFiles
    mainClass.set("dev.autumn.demo.server.ServerKt")
    
    dependsOn(tasks.named("jsBrowserDistribution"))
    val jsBrowserDistribution = tasks.named("jsBrowserDistribution").get()
    environment("AUTUMN_DEMO_WEB_DIR", jsBrowserDistribution.outputs.files.singleFile.absolutePath)
}
val runDemoWasmServer by tasks.registering(JavaExec::class) {
    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main") as org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
    classpath = jvmMain.output.allOutputs + jvmMain.runtimeDependencyFiles
    mainClass.set("dev.autumn.demo.server.ServerKt")
    
    dependsOn(tasks.named("wasmJsBrowserDistribution"))
    val wasmJsBrowserDistribution = tasks.named("wasmJsBrowserDistribution").get()
    environment("AUTUMN_DEMO_WEB_DIR", wasmJsBrowserDistribution.outputs.files.singleFile.absolutePath)
}

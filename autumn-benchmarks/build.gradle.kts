plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlinx.benchmark") version "0.4.11"
    id("dev.autumn.plugin") version "1.0.2"
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "23"
            }
        }
    }
    
    linuxX64 {
        binaries {
            executable {
                entryPoint = "dev.autumn.benchmark.main"
            }
        }
    }
    
    sourceSets {
        val buildDeps = Action<org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet> {
            dependencies {
                implementation(project(":autumn-core"))
                implementation(project(":autumn-observatory"))
                implementation(project(":autumn-buckets"))
                implementation(project(":autumn-ui"))
                implementation(project(":autumn-resolver"))
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.11")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            }
        }
        val jvmMain by getting { buildDeps.execute(this) }
        val linuxX64Main by getting { buildDeps.execute(this) }
    }
}

val runComparison by tasks.registering(JavaExec::class) {
    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main") as org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
    classpath = jvmMain.output.allOutputs + jvmMain.runtimeDependencyFiles
    mainClass.set("dev.autumn.benchmark.OrderBookComparisonKt")
}

val runNativeComparison by tasks.registering(Exec::class) {
    val nativeLinkTask = tasks.named("linkReleaseExecutableLinuxX64")
    dependsOn(nativeLinkTask)
    doFirst {
        val linuxTarget = kotlin.targets.getByName("linuxX64") as org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
        val exe = linuxTarget.binaries.getExecutable("", org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE).outputFile
        commandLine(exe.absolutePath)
    }
}

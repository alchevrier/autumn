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
    
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":autumn-core"))
                implementation(project(":autumn-buckets"))
                implementation(project(":autumn-ui"))
                implementation(project(":autumn-resolver"))
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.11")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            }
        }
    }
}

benchmark {
    targets {
        register("jvm")
    }
}

val runComparison by tasks.registering(JavaExec::class) {
    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main") as org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
    classpath = jvmMain.output.allOutputs + jvmMain.runtimeDependencyFiles
    mainClass.set("dev.autumn.benchmark.OrderBookComparisonKt")
}

tasks.register("printClasspath") {
    doLast {
        val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main") as org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
        val cp = (jvmMain.output.allOutputs + jvmMain.runtimeDependencyFiles).files.joinToString(":")
        println("CLASSPATH=$cp")
    }
}

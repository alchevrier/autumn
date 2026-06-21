plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlinx.benchmark") version "0.4.11"
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

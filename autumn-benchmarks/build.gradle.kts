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
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.11")
            }
        }
    }
}

benchmark {
    targets {
        register("jvm")
    }
}

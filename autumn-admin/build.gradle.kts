plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":autumn-core"))
                implementation("io.ktor:ktor-server-core:2.3.11")
                implementation("io.ktor:ktor-server-netty:2.3.11")
                implementation("io.ktor:ktor-server-content-negotiation:2.3.11")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")
                implementation("org.slf4j:slf4j-simple:2.0.12")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-server-test-host:2.3.11")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            }
        }
    }
}

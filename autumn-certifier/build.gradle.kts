plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    `java-gradle-plugin`
}

group = "io.github.alchevrier"
version = "1.0.0"

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "23"
    targetCompatibility = "23"
}

dependencies {
    implementation(gradleApi())
    // We'll need kotlinx-serialization-json to parse topology.json later
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}

gradlePlugin {
    plugins {
        create("autumnCertifier") {
            id = "dev.autumn.certifier"
            implementationClass = "dev.autumn.certifier.AutumnCertifierPlugin"
        }
    }
}

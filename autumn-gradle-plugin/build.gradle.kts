plugins {
    kotlin("jvm") version "2.1.21"
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.github.alchevrier"
version = "1.0.2"

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "23"
    targetCompatibility = "23"
}

dependencies {
    implementation(gradleApi())
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:2.1.21")
}

gradlePlugin {
    plugins {
        create("autumnPlugin") {
            id = "dev.autumn.plugin"
            implementationClass = "dev.autumn.gradle.AutumnGradlePlugin"
        }
    }
}

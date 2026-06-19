plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

group = "dev.autumn"
version = "1.0.2"

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

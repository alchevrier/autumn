plugins {
    kotlin("jvm")
    `java-gradle-plugin`
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

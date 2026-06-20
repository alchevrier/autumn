plugins {
    kotlin("jvm")
}

group = "io.github.alchevrier"
version = "1.0.2"

tasks.withType<JavaCompile> {
    sourceCompatibility = "23"
    targetCompatibility = "23"
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.1.21")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

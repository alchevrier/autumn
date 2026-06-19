plugins {
    kotlin("jvm")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "23"
    targetCompatibility = "23"
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.1.21")
    
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.6.0") // Needed for testing the compiler plugin
}

// Ensure the compiler plugin is published/packaged appropriately if needed later.

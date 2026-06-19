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
}

// Ensure the compiler plugin is published/packaged appropriately if needed later.

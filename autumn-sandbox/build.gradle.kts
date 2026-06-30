plugins {
    kotlin("jvm")
    application
    id("dev.autumn.certifier")
}

application {
    mainClass.set("dev.autumn.sandbox.MainKt")
}

dependencies {
    implementation(project(":autumn-core"))
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "23"
    targetCompatibility = "23"
}

// We wire the compiler plugin directly into the free compiler arguments of this module
// without needing to publish or configure composite builds!
val pluginJarTask = project(":autumn-compiler-plugin").tasks.named<org.gradle.api.tasks.bundling.Jar>("jar")

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(pluginJarTask)
    compilerOptions {
        freeCompilerArgs.add(pluginJarTask.flatMap { it.archiveFile }.map { "-Xplugin=${it.asFile.absolutePath}" })
    }
}


plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":autumn-core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Dogfooding the compiler plugin to enforce zero-allocation within our own buckets
// Temporarily bypassed because multiplatform resolution of custom plugin tasks differs from standard JVM projects.
/*
val pluginJarTask = project(":autumn-compiler-plugin").tasks.named<org.gradle.api.tasks.bundling.Jar>("jar")

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(pluginJarTask)
    compilerOptions {
        freeCompilerArgs.add(pluginJarTask.flatMap { it.archiveFile }.map { "-Xplugin=${it.asFile.absolutePath}" })
    }
}
*/

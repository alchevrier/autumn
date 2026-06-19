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
val autumnCompilerPlugin by configurations.creating
dependencies {
    autumnCompilerPlugin(project(":autumn-compiler-plugin"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(autumnCompilerPlugin)
    compilerOptions {
        val pluginFiles = autumnCompilerPlugin.files
        freeCompilerArgs.addAll(provider {
            pluginFiles.map { "-Xplugin=${it.absolutePath}" }
        })
    }
}
*/

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform") version "2.0.1"
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.1.4") // Targets 2024.1
        bundledPlugin("org.jetbrains.kotlin") // We'll need Kotlin plugin features to parse FIR/IR 
        pluginVerifier()
        zipSigner()
        instrumentationTools()
    }

    // Temporarily linking autumn-compiler-plugin until we extract autumn-compiler-shared
    implementation(project(":autumn-compiler-plugin")) 
}

intellijPlatform {
    pluginConfiguration {
        id.set("dev.autumn.ide")
        name.set("Autumn Performance Center")
        version.set(project.version.toString())
        vendor {
            name.set("A. L. Chevrier")
            url.set("https://github.com/alchevrier/autumn")
        }
        description.set("""
            <p>Circuit-based hardware performance constraints and topology visualization for the Autumn Framework.</p>
            <ul>
                <li><strong>Inline Execution Lenses:</strong> Translates ILP pressure and cycle limits directly inline above Autumn channels.</li>
                <li><strong>Spatial Budgets:</strong> L1/L3 Cache tracking for local instances.</li>
                <li><strong>Performance Center:</strong> Topology rendering and cross-thread pipeline graphs natively in the IDE.</li>
            </ul>
        """.trimIndent())
    }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
            freeCompilerArgs.add("-Xopt-in=kotlin.RequiresOptIn")
        }
    }
}

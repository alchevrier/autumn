plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform") version "2.0.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    id("org.jetbrains.compose") version "1.7.3"
}



repositories {
    google()

    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://packages.jetbrains.team/maven/p/jewel/events")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    intellijPlatform {
        defaultRepositories()
    }
}



dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.1.4")
        bundledPlugin("org.jetbrains.kotlin")

    // Removing Jewel temporarily to verify standard Compose Desktop builds fine
    // implementation("org.jetbrains.jewel:jewel-ide-laf-bridge-241:1.3.1")
    // implementation("org.jetbrains.jewel:jewel-int-ui-standalone-241:1.3.1")


        pluginVerifier()
        zipSigner()
        instrumentationTools()
    }
    implementation(project(":autumn-compiler-plugin"))
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.material)
    
    // Core serialization to parse the artifact
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
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
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xopt-in=kotlin.RequiresOptIn")
        }
    }
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}

plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.1.21" apply false
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

allprojects {
    group = "dev.autumn"
    version = "1.0.2"

    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
    apply(plugin = "maven-publish")
}

subprojects {
    plugins.withType<MavenPublishPlugin> {
        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/alchevrier/autumn")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                        password = System.getenv("GITHUB_TOKEN")
                    }
                }
            }
        }
    }
}

subprojects {
    // Workaround for Kover + Gradle 9 temporary directory race conditions
    tasks.withType<Test>().configureEach {
        doFirst {
            project.layout.buildDirectory.dir("tmp/${name}").get().asFile.mkdirs()
        }
    }
}

plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.1.21" apply false
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

allprojects {
    group = "io.github.alchevrier"
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
    apply(plugin = "signing")

    plugins.withType<MavenPublishPlugin> {
        configure<PublishingExtension> {
            repositories {
                
                maven {
                    name = "Sonatype"
                    val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                    val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                    url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
                    credentials {
                        username = System.getenv("OSSRH_USERNAME")
                        password = System.getenv("OSSRH_PASSWORD")
                    }
                }
            }

            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set(project.name)
                    description.set("Zero-allocation circuit-based KMP UI & state engine")
                    url.set("https://github.com/alchevrier/autumn")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    developers {
                        developer {
                            id.set("alchevrier")
                            name.set("A. L. Chevrier")
                            email.set("alchevrier@users.noreply.github.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/alchevrier/autumn.git")
                        developerConnection.set("scm:git:ssh://github.com/alchevrier/autumn.git")
                        url.set("https://github.com/alchevrier/autumn/")
                    }
                }
            }
        }
    }

    extensions.findByType<SigningExtension>()?.apply {
        val signingKey = System.getenv("SIGNING_KEY")
        val signingPassword = System.getenv("SIGNING_PASSWORD")
        if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            val ext = extensions.getByType<PublishingExtension>()
            sign(ext.publications)
        }
    }

    // Workaround for Kover + Gradle 9 temporary directory race conditions
    tasks.withType<Test>().configureEach {
        doFirst {
            project.layout.buildDirectory.dir("tmp/${name}").get().asFile.mkdirs()
        }
    }
}

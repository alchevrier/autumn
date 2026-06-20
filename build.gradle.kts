plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.1.21" apply false
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
    id("com.vanniktech.maven.publish") version "0.30.0" apply false
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
    apply(plugin = "com.vanniktech.maven.publish")

    mavenPublishing {
        publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
        signAllPublications()
        
        coordinates("io.github.alchevrier", project.name, "1.0.2")

        pom {
            name.set(project.name)
            description.set("Zero-allocation circuit-based KMP UI & state engine")
            inceptionYear.set("2026")
            url.set("https://github.com/alchevrier/autumn")
            licenses {
                license {
                    name.set("The MIT License")
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

    // Workaround for Kover + Gradle 9 temporary directory race conditions
    tasks.withType<Test>().configureEach {
        doFirst {
            project.layout.buildDirectory.dir("tmp/${name}").get().asFile.mkdirs()
        }
    }
}

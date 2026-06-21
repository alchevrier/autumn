import re

with open("build.gradle.kts", "r") as f:
    text = f.read()

# Replace `subprojects {` to `subprojects { if (project.name != "autumn-benchmarks") {` around maven.publish
# Actually, the easiest is to just clear apply(plugin = "com.vanniktech... ") for it.
# We will do:
text = text.replace('apply(plugin = "com.vanniktech.maven.publish")', 'if (project.name != "autumn-benchmarks") {\n        apply(plugin = "com.vanniktech.maven.publish")\n    }')
text = text.replace('configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {', 'plugins.withId("com.vanniktech.maven.publish") {\n        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {')
text = text.replace('// Workaround for Kover', '}\n\n    // Workaround for Kover')

with open("build.gradle.kts", "w") as f:
    f.write(text)

package dev.autumn.certifier

import org.gradle.api.Plugin
import org.gradle.api.Project

class AutumnCertifierPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("autumnCertify", AutumnCertifierTask::class.java) { task ->
            task.group = "Autumn Verification"
            task.description = "Executes the Hybrid WCET verification combining the static CFG from topology.json and empirical perf profiling bounds."
            
            // Look for the native executable produced by Kotlin Multiplatform linuxX64 target.
            // Adjust to the right file path dynamically if needed.
            task.topologyFile.set(project.layout.buildDirectory.file("reports/autumn/topology.json"))
        }
    }
}

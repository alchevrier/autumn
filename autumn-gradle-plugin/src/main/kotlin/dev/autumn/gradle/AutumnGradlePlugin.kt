package dev.autumn.gradle

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class AutumnGradlePlugin : KotlinCompilerPluginSupportPlugin {
    
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return project.provider { emptyList() }
    }

    override fun getCompilerPluginId(): String = "dev.autumn.compiler"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "dev.autumn",
        artifactId = "autumn-compiler-plugin",
        version = "1.0.2" // In a real project, this aligns with project version
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        // As a starting point, it's applicable to all KMP compilations where the plugin is applied
        return true
    }
}

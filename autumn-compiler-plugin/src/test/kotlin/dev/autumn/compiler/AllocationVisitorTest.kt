package dev.autumn.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

@OptIn(ExperimentalCompilerApi::class)
class AllocationVisitorTest {

    @Test
    fun `allocations not marked @LongLived emit a warning`() {
        val kotlinSource = SourceFile.kotlin(
            "main.kt", """
            package dev.autumn.test
            
            class SomeState
            
            fun doSomething() {
                val state = SomeState() // This is an allocation
            }
            """.trimIndent()
        )

        val result = KotlinCompilation().apply {
            sources = listOf(kotlinSource)
            compilerPluginRegistrars = listOf(AutumnCompilerPluginRegistrar())
            inheritClassPath = true
            messageOutputStream = System.out
            languageVersion = "2.1" 
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertContains(result.messages, "Heap allocation detected in strict zero-allocation scope")
    }

    @Test
    fun `allocations inside @LongLived class do not emit a warning`() {
        val kotlinSource = SourceFile.kotlin(
            "main.kt", """
            package dev.autumn.annotations
            
            annotation class LongLived
            
            class SomeState
            
            @LongLived
            class AppBootstrap {
                val state = SomeState() // Allowed
            }
            """.trimIndent()
        )

        val result = KotlinCompilation().apply {
            sources = listOf(kotlinSource)
            compilerPluginRegistrars = listOf(AutumnCompilerPluginRegistrar())
            inheritClassPath = true
            messageOutputStream = System.out
            languageVersion = "2.1" 
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val hasWarning = result.messages.contains("Heap allocation detected in strict zero-allocation scope")
        assertEquals(false, hasWarning, "Should not have emitted warning for @LongLived class")
    }
}

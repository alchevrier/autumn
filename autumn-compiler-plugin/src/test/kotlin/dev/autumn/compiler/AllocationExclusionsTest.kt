package dev.autumn.compiler

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AllocationExclusionsTest {

    @Test
    fun `blank fqName returns false`() {
        assertFalse(AllocationExclusions.isSafeType(""), "Empty string should be false")
        assertFalse(AllocationExclusions.isSafeType("   "), "Blank string should be false")
    }

    @Test
    fun `exceptions and errors return true`() {
        assertTrue(AllocationExclusions.isSafeType("java.lang.RuntimeException"))
        assertTrue(AllocationExclusions.isSafeType("kotlin.Exception"))
        assertTrue(AllocationExclusions.isSafeType("dev.autumn.MyCustomException"))
        
        assertTrue(AllocationExclusions.isSafeType("java.lang.OutOfMemoryError"))
        assertTrue(AllocationExclusions.isSafeType("kotlin.Error"))
        assertTrue(AllocationExclusions.isSafeType("dev.autumn.FatalError"))
    }

    @Test
    fun `java_lang namespace returns true`() {
        // Essential intrinsics sitting in java.lang 
        assertTrue(AllocationExclusions.isSafeType("java.lang.Object"))
        assertTrue(AllocationExclusions.isSafeType("java.lang.Integer"))
        assertTrue(AllocationExclusions.isSafeType("java.lang.Thread"))
    }

    @Test
    fun `kotlin_String returns true`() {
        assertTrue(AllocationExclusions.isSafeType("kotlin.String"))
    }

    @Test
    fun `allocations outside whitelist return false`() {
        // App states and standard objects
        assertFalse(AllocationExclusions.isSafeType("dev.autumn.AppState"))
        
        // Collections - strictly forbidden in hot paths in our architecture
        assertFalse(AllocationExclusions.isSafeType("kotlin.collections.ArrayList"))
        assertFalse(AllocationExclusions.isSafeType("kotlin.collections.HashMap"))
        assertFalse(AllocationExclusions.isSafeType("java.util.List"))
        
        // Fakes that might try to trick the strings
        assertFalse(AllocationExclusions.isSafeType("dev.autumn.ExceptionMaker"))
        assertFalse(AllocationExclusions.isSafeType("dev.autumn.ErrorFactory"))
    }
}

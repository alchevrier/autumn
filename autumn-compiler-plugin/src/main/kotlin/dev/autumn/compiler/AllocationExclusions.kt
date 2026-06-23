package dev.autumn.compiler

/**
 * Shared logic for checking if an allocation type is safely excluded from strict zero-allocation checks.
 * This is used by both the Frontend (FIR) and Backend (IR) compiler phases.
 */
object AllocationExclusions {

    fun isSafeType(fqName: String): Boolean {
        if (fqName.isBlank()) return false
        
        return fqName.endsWith("Exception") || 
               fqName.endsWith("Error") ||
               fqName.endsWith("Flyweight") || // Value classes do not heap allocate!
               fqName.startsWith("java.lang.") ||
               fqName == "kotlin.String"
    }
}

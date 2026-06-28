package dev.autumn.annotations

/**
 * Statically verifies that the compiler-generated IR for this function 
 * executes within the specified hardware cycle budget.
 * Functions exceeding this limit will fail to compile.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class CycleBudget(
    val limit: Int
)

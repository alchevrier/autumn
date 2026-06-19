package dev.autumn.annotations

/**
 * Marks a field or property as a source of truth for a memory allocation budget.
 * 
 * Autumn's compiler plugin uses this annotation to derive pool sizes at build time.
 * The value provided should map to the corresponding configuration key.
 * 
 * Example: `@BudgetSource("activePagesOnScreen")`
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.BINARY)
annotation class BudgetSource(val name: String)

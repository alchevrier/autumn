package dev.autumn.annotations

/**
 * Instructs the Autumn Compiler Plugin to preprocess the budget calculation 
 * and inject a static constant or pre-computed bounded size expression 
 * into the ByteArray initialization at compile time.
 */
@Target(AnnotationTarget.EXPRESSION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class InjectBudget(
    val category: String = ""
)

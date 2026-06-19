package dev.autumn.annotations

/**
 * Instructs the Autumn Compiler Plugin to preprocess the budget calculation 
 * and inject a static constant or pre-computed bounded size expression 
 * into the ByteArray initialization at compile time.
 * 
 * Aligning with Circuit-Based Programming, this allows the IDE and compiler 
 * to parse the layout configuration directly and display the allocated slot 
 * budgets inline.
 */
@Target(AnnotationTarget.EXPRESSION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class InjectBudget(
    val entityScope: String = "",
    val singleEntityCount: Int = 0,
    val paginatedCollectionCount: Int = 0,
    val slotsPerPage: Int = 0
)

package dev.autumn.config

/**
 * Calculates allocation budgets derived strictly from declared configuration entities.
 * Following ADR-0015, these formulas compute the deterministic hardware matrices 
 * based on real schema values like the number of single entities, active pages, and items per page.
 */
object BudgetCalculator {

    fun calculateEntitySlotBudget(
        singleEntityCount: Int,
        paginatedCollectionCount: Int,
        activePagesOnScreen: Int = 1,
        bufferPages: Int = 1,
        evictionBoundaryPages: Int = 1,
        slotsPerPage: Int // derived from config interaction pageSize
    ): Int {
        val singleSlots = singleEntityCount
        
        // ADR-0015: hotSlotCount = (activePagesOnScreen + 2 × bufferPages + 2 × evictionBoundaryPages) × slotsPerPage
        val hotSlotCount = (activePagesOnScreen + (2 * bufferPages) + (2 * evictionBoundaryPages)) * slotsPerPage
        val listSlots = paginatedCollectionCount * hotSlotCount
        
        return singleSlots + listSlots
    }

    fun calculateConfigResourceBudget(declaredResources: Int, variantsToLoad: Int = 1): Int {
        return declaredResources * variantsToLoad
    }
}

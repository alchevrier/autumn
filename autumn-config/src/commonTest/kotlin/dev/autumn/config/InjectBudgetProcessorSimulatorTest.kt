package dev.autumn.config

import dev.autumn.annotations.InjectBudget
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A test illustrating how a K2 Compiler Plugin or KSP Processor
 * will interpret the @InjectBudget annotation during the build phase.
 * 
 * In a real circuit-based environment, this logic executes statically 
 * replacing the right-hand-side expression with a constant Int.
 */
class InjectBudgetProcessorSimulatorTest {

    // Simulates the compiler plugin visiting an AST node with @InjectBudget
    private fun simulateCompilerPluginProcessing(annotation: InjectBudget): Int {
        // The plugin uses BudgetCalculator to compute the correct memory bounds
        return BudgetCalculator.calculateEntitySlotBudget(
            singleEntityCount = annotation.singleEntityCount,
            paginatedCollectionCount = annotation.paginatedCollectionCount,
            slotsPerPage = annotation.slotsPerPage
            // Defaulting the sliding window parameters to the standard config (active=1, buffer=1, eviction=1)
        )
    }

    @Test
    fun `compiler plugin correctly transforms layout annotation into static budget`() {
        // 1. Developer annotates a property array in code:
        // @InjectBudget(entityScope = "home_feed", singleEntityCount = 10, paginatedCollectionCount = 2, slotsPerPage = 20)
        // val items = IntArray(...)
        
        val homeFeedBudget = InjectBudget(
            entityScope = "home_feed",
            singleEntityCount = 10,
            paginatedCollectionCount = 2,
            slotsPerPage = 20
        )

        // 2. K2 Plugin evaluates it at compile time
        val injectedStaticMemorySize = simulateCompilerPluginProcessing(homeFeedBudget)

        // 3. Verifies the statically injected constraint matches ADR-0015!
        // 2 list collections × 20 slotsPerPage × (1 active + 2 buffer + 2 eviction) pages
        // = 2 × 20 × 5 = 200 list slots
        // + 10 single entity slots
        // = 210 total slots
        assertEquals(210, injectedStaticMemorySize, "The plugin should extract properties and statically inject 210 slots")
    }
}

package dev.autumn.config

import kotlin.test.Test
import kotlin.test.assertEquals

class BudgetCalculatorTest {

    @Test
    fun `calculateEntitySlotBudget uses sliding window semantics derived from ADR-0015`() {
        // Given 10 single entities and 2 lists that show 20 items per page
        val singleCount = 10
        val pageCollectionCount = 2
        val slotsPerPage = 20

        // In standard configuration (1 active page, 1 buffer before/after, 1 eviction boundary before/after):
        // Sliding window should hold: (1 active) + (2 * 1 buffer) + (2 * 1 eviction) = 5 pages total hot slots
        // 5 pages * 20 items per page = 100 slots per collection
        // 2 collections = 200 slots
        // Plus single static entities (10)
        // Total expected = 210
        val expected = 210

        val actual = BudgetCalculator.calculateEntitySlotBudget(
            singleEntityCount = singleCount,
            paginatedCollectionCount = pageCollectionCount,
            slotsPerPage = slotsPerPage
        )

        assertEquals(expected, actual, "Budget should account for the entire sliding window presentation tier")
    }

    @Test
    fun `calculateEntitySlotBudget supports custom presentation constraints`() {
        // e.g. An iPad UI that shows 2 columns (2 active pages at a time)
        // and buffers aggressively (2 pages), but drops boundaries quickly (0 eviction)
        val actual = BudgetCalculator.calculateEntitySlotBudget(
            singleEntityCount = 5,
            paginatedCollectionCount = 1,
            activePagesOnScreen = 2,
            bufferPages = 2,
            evictionBoundaryPages = 0,
            slotsPerPage = 15 // 15 slots per page block
        )

        // Hot Slots = (2 active + (2 * 2 buffer) + (2 * 0 eviction)) * 15 slotsPerPage
        //           = (2 + 4 + 0) * 15
        //           = 6 * 15 = 90
        // Total = 5 singles + (1 list * 90) = 95
        assertEquals(95, actual)
    }

    @Test
    fun `calculateConfigResourceBudget scales with variants`() {
        val actual = BudgetCalculator.calculateConfigResourceBudget(
            declaredResources = 50,
            variantsToLoad = 2 // e.g. base config + country override
        )
        assertEquals(100, actual)
    }
}

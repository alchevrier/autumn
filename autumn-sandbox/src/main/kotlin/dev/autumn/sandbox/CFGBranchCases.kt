package dev.autumn.sandbox

import dev.autumn.annotations.CycleBudget
import dev.autumn.annotations.LongLived
import dev.autumn.annotations.Observe

class CFGBranchCases {
    val output = LongArray(10)

    /**
     * ✅ CFG ILP BRANCH SOLVER AUDIT
     *
     * This handler contains mutually exclusive execution paths.
     * In a purely static additive model (naive), the cycle cost would count all branches as if they execute sequentially.
     * The @InjectTopology(wcetAuditable=true) setting uses the extracted Control Flow Graph (CFG)
     * to evaluate the true maximal path independently, giving real-world WCET bounds.
     */
    @Observe("branchingHandler")
    @CycleBudget(limit = 150)
    @LongLived
    fun handleMarketState(state: Int, price: Long) {
        if (state == 1) {
            output[1] = price * 2
        } else if (state == 2) {
            output[2] = price * 4
        } else {
            output[0] = price
        }
    }
}
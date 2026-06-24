package dev.autumn.sandbox

import dev.autumn.annotations.HotPath
import dev.autumn.annotations.ThreadCacheBudget

class CacheBudgetCases {

    // ❌ ERROR: Total stack size is 20 bytes, Exceeds 16
    // UNCOMMENT NEXT BLOCK TO TRIGGER COMPILATION ERROR:
    // @HotPath
    // @ThreadCacheBudget(16)
    // fun fastCoreLoopFail(a: Int, b: Long) {
    //     val c = a.toLong() + b
    // }

    // ✅ ALLOWED: Fits smoothly (20 bytes used within 24 limit)
    @HotPath
    @ThreadCacheBudget(24)
    fun compliantCoreLoop(a: Int, b: Long) {
        val c = a.toLong() + b
    }

    // ✅ ALLOWED: Only uses Math primitive spaces (Int:4, Float:4 -> exactly 8 bytes)
    @HotPath
    @ThreadCacheBudget(8)
    fun tinyBudgetFit(a: Int) {
        val b: Float = 1.0f
    }

    // ❌ ERROR: Very close, but 1 byte off (Double:8, Byte:1 -> 9 bytes used vs 8 allowed)
    // UNCOMMENT NEXT BLOCK TO TRIGGER COMPILATION ERROR:
    // @HotPath
    // @ThreadCacheBudget(8)
    // fun tinyBudgetFail(a: Byte) {
    //     val d: Double = 1.0
    // }

    // ✅ ALLOWED: Safely fits abstract pointers (String:8, String:8 -> 16 bytes max allowed)
    @HotPath
    @ThreadCacheBudget(16)
    fun objectReference(ref: String) {
        val otherRef: String = "Hello"
    }

    // ❌ ERROR: Multi-variable sequential allocations that blow past the budget (24 > 16)
    // UNCOMMENT NEXT BLOCK TO TRIGGER COMPILATION ERROR:
    // @HotPath
    // @ThreadCacheBudget(16)
    // fun greedyLocals(a: Int) {
    //     val b: Long = 0L
    //     val c: Long = 0L
    //     val d: Int = 0
    // }

    // ✅ ALLOWED: Inline value class is structurally unwrapped (OrderEventFlyweight = index Int -> 4 bytes)
    @HotPath
    @ThreadCacheBudget(8)
    fun valueClassFit(order: OrderEventFlyweight) {
        val qty: Int = 100 // + 4 bytes = exactly 8
    }

    // ❌ ERROR: Inline value class calculation verifies true Int size (4 + 8 > 8 bytes)
    // UNCOMMENT NEXT BLOCK TO TRIGGER COMPILATION ERROR:
    // @HotPath
    // @ThreadCacheBudget(8)
    // fun valueClassFail(order: OrderEventFlyweight) {
    //     val quantityLong: Long = 100L // 4 (param) + 8 (local) = 12 > 8
    // }
}

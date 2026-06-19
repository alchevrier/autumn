package dev.autumn.sandbox

import dev.autumn.annotations.LongLived

class AppState

@LongLived
class ApplicationBootstrap {
    // ✅ ALLOWED: Configured inside an explicit @LongLived initialization scope
    val state = AppState()
}

fun myHotPath() {
    // ✅ ALLOWED: Whitelisted primitives, Strings, and Exceptions that sit outside constraints
    val throwaway = Exception("Valid bailout allocation")

    // ❌ ERROR: Heap-allocated object on hot path
    // UNCOMMENT NEXT LINE TO TRIGGER COMPILATION ERROR:
    // val state = AppState()

    // ❌ ERROR: Heap-allocated native arrays are strictly blocked (use Budgets!)
    // UNCOMMENT NEXT LINE TO TRIGGER COMPILATION ERROR:
    // val primitives = IntArray(10)

    // ❌ ERROR: Standard Collections allocations are strictly blocked
    // UNCOMMENT NEXT LINE TO TRIGGER COMPILATION ERROR:
    // val list = ArrayList<String>()
}

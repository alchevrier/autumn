package dev.autumn.observatory

import dev.autumn.memory.AutumnMemoryBank

/**
 * A zero-allocation flat array structure designed to sit off-heap and capture 
 * executing clock cycles. Uses a simple ring-buffer cursor.
 */
class LatencyHistogram(
    val baseOffset: Int,
    val capacity: Int
) {
    private var writeCursor: Int = 0
    private var isFilled: Boolean = false

    /**
     * Record a raw nanosecond or cycle delta directly over the memory bank.
     */
    fun recordDelta(delta: Long) {
        val pointer = baseOffset + (writeCursor * 8)
        AutumnMemoryBank.setLong(pointer, delta)
        
        writeCursor++
        if (writeCursor >= capacity) {
            writeCursor = 0
            isFilled = true
        }
    }

    /**
     * Expensive calculation! Should only be executed via a Cold Observatory thread, 
     * never on the Hot Execution Arbiter.
     */
    fun calculatePercentile(percentile: Double): Long {
        val totalElements = if (isFilled) capacity else writeCursor
        if (totalElements == 0) return 0L

        val heapArray = LongArray(totalElements)
        for (i in 0 until totalElements) {
            val pointer = baseOffset + (i * 8)
            heapArray[i] = AutumnMemoryBank.getLong(pointer)
        }
        
        heapArray.sort()
        
        val index = ((percentile / 100.0) * totalElements).toInt()
        val clampedIndex = index.coerceIn(0, totalElements - 1)
        
        return heapArray[clampedIndex]
    }
}

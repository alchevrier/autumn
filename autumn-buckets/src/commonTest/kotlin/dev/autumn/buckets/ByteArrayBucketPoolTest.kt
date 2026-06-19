package dev.autumn.buckets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// 1. The Flyweight View: UI interacts with this object. No memory allocated on reads.
class PointView(private val pool: PointBucketPool) {
    var currentIndex: Int = 0
    val x: Int get() = pool.getX(currentIndex)
    val y: Int get() = pool.getY(currentIndex)
}

// 2. The Bucket implementation mapping logical objects to the raw byte array
class PointBucketPool(capacity: Int) : ByteArrayBucketPool<PointView>(capacity, recordSizeInBytes = 8) {
    // Single shared instance for UI iteration (zero-allocation boundary)
    private val flyweight = PointView(this)

    // Byte offsets inside a single record (Int = 4 bytes)
    private val OFFSET_X = 0
    private val OFFSET_Y = 4

    fun getX(index: Int): Int = readInt((index * recordSizeInBytes) + OFFSET_X)
    fun getY(index: Int): Int = readInt((index * recordSizeInBytes) + OFFSET_Y)
    
    private fun setX(index: Int, value: Int) = writeInt((index * recordSizeInBytes) + OFFSET_X, value)
    private fun setY(index: Int, value: Int) = writeInt((index * recordSizeInBytes) + OFFSET_Y, value)

    fun add(x: Int, y: Int) {
        if (size >= capacity) throw IllegalStateException("Bucket capacity exceeded")
        setX(size, x)
        setY(size, y)
        size++ // increment active items
    }

    override fun get(index: Int): PointView {
        if (index < 0 || index >= size) throw IndexOutOfBoundsException("Index $index out of bounds")
        // Simply shift the cursor of our shared object!
        flyweight.currentIndex = index
        return flyweight
    }
}

class ByteArrayBucketPoolTest {

    @Test
    fun `test adding and reading points without allocation`() {
        val pool = PointBucketPool(capacity = 3)
        
        // Background logic adds elements directly via integers
        pool.add(10, 20)
        pool.add(100, 200)

        assertEquals(2, pool.size)

        // UI Fetching Item 0
        val p0 = pool[0]
        assertEquals(10, p0.x)
        assertEquals(20, p0.y)

        // UI Fetching Item 1
        val p1 = pool[1] 
        assertEquals(100, p1.x)
        assertEquals(200, p1.y)
        
        // ZERO-ALLOCATION PROOF: p0 and p1 point to the SAME flyweight object in memory!
        // Both now reflect index 1 since we last asked for pool[1].
        // This is safe because UI widgets pull the data iteratively right when they render it.
        assertEquals(100, p0.x) 
    }

    @Test
    fun `test capacity boundaries and clearing`() {
        val pool = PointBucketPool(capacity = 2)
        pool.add(5, 5)
        pool.add(6, 6)

        assertFailsWith<IllegalStateException> {
            pool.add(7, 7) // Exceeds capacity of 2
        }

        // Logic wipes the bucket
        pool.clear()
        assertEquals(0, pool.size)

        // Memory was never re-allocated, we can just insert over the old bytes!
        pool.add(8, 8)
        assertEquals(1, pool.size)
        assertEquals(8, pool[0].x)
    }

    @Test
    fun `test simulated UI iteration loop`() {
        val pool = PointBucketPool(capacity = 100)
        
        // Simulate background loop adding 50 items
        for (i in 0 until 50) {
            pool.add(x = i, y = i * 2)
        }

        // Simulate UI Rendering Loop (Zero Allocations on exactly 50 reads!)
        var sumX = 0
        for (i in 0 until pool.size) {
            val point = pool[i] // No heap allocation here, just moving the cursor
            sumX += point.x     // Direct bit-shift read from ByteArray
        }

        assertEquals(1225, sumX) // Sum of 0..49
    }
}

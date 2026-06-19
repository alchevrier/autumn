package dev.autumn.buckets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import dev.autumn.annotations.LongLived

// 1. The SBE Flyweight Decoder
// Extends SbeDecoder which natively holds `buffer` and `offset` cursors.
class PointDecoder : SbeDecoder() {
    // Exact byte offsets matching the strict SBE schema
    private val OFFSET_X = 0
    private val OFFSET_Y = 4

    var x: Int
        get() = readInt(OFFSET_X)
        set(value) = writeInt(OFFSET_X, value)

    var y: Int
        get() = readInt(OFFSET_Y)
        set(value) = writeInt(OFFSET_Y, value)
}

// 2. The Bucket implementation mapping logical objects to the raw byte array
@LongLived
class PointBucketPool(capacity: Int) : ByteArrayBucketPool<PointDecoder>(
    capacity = capacity, 
    recordSizeInBytes = 8, 
    flyweight = PointDecoder()
)

@LongLived
class ByteArrayBucketPoolTest {

    @Test
    fun `test adding and reading points without allocation using SBE`() {
        val pool = PointBucketPool(capacity = 3)
        
        // Background logic appends elements directly using the encoder/decoder flyweight
        pool.append().apply {
            x = 10
            y = 20
        }
        
        pool.append().apply {
            x = 100
            y = 200
        }

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
        // Both now reflect index 1 since we last asked for pool[1] which shifted the decoder cursor!
        assertEquals(100, p0.x) 
    }

    @Test
    fun `test capacity boundaries and clearing`() {
        val pool = PointBucketPool(capacity = 2)
        pool.append().apply { x = 5; y = 5 }
        pool.append().apply { x = 6; y = 6 }

        assertFailsWith<IllegalStateException> {
            pool.append() // Exceeds capacity of 2
        }

        // Logic wipes the bucket
        pool.clear()
        assertEquals(0, pool.size)

        // Memory was never re-allocated, we can just insert over the old bytes!
        pool.append().apply { x = 8; y = 8 }
        assertEquals(1, pool.size)
        assertEquals(8, pool[0].x)
    }

    @Test
    fun `test simulated UI iteration loop`() {
        val pool = PointBucketPool(capacity = 100)
        
        // Simulate background loop adding 50 items
        for (i in 0 until 50) {
            pool.append().apply {
                x = i
                y = i * 2
            }
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

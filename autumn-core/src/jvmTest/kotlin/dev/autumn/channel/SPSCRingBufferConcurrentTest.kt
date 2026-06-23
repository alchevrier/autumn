package dev.autumn.channel

import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class SPSCRingBufferConcurrentTest {

    @Test
    fun `test concurrent producer and consumer`() {
        // A standard power-of-two capacity
        val capacity = 1024
        val buffer = SPSCRingBuffer(capacity)
        
        // Pass 10 million elements through the ring buffer to heavily stress the Acquire/Release caches
        val totalItems = 10_000_000
        
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(2)

        var producerLoopCount = 0
        var consumerLoopCount = 0

        val producer = thread(start = false, name = "producer-thread") {
            startLatch.await()
            for (i in 0 until totalItems) {
                var idx = -1
                // Wait-free loop mimicking hardware clock tick retries
                while (idx == -1) {
                    idx = buffer.offer()
                }
                
                // Real usage would write to SoA array at `idx` here
                
                buffer.commitOffer()
                producerLoopCount++
            }
            doneLatch.countDown()
        }

        val consumer = thread(start = false, name = "consumer-thread") {
            startLatch.await()
            for (i in 0 until totalItems) {
                var idx = -1
                // Wait-free loop
                while (idx == -1) {
                    idx = buffer.poll()
                }
                
                // Real usage would read from SoA array at `idx` here
                
                buffer.commitPoll()
                consumerLoopCount++
            }
            doneLatch.countDown()
        }

        producer.start()
        consumer.start()

        // Release the latch to start threads simultaneously
        val startTime = System.nanoTime()
        startLatch.countDown()
        
        // Wait for both threads to finish exactly 10 million items
        doneLatch.await()
        val durationMs = (System.nanoTime() - startTime) / 1_000_000

        println("SPSC Buffer processed $totalItems items in ${durationMs}ms")
        
        assertEquals(totalItems, producerLoopCount)
        assertEquals(totalItems, consumerLoopCount)
    }
}

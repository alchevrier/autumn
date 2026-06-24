package dev.autumn.benchmark

import kotlinx.benchmark.*
import java.util.concurrent.ConcurrentHashMap
import java.lang.invoke.MethodHandles

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(BenchmarkTimeUnit.NANOSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
open class AutumnConcurrentBenchmark {

    @Volatile
    private var running = true

    // Cache-line padding to prevent False Sharing with the Seqlock
    private val pad0 = LongArray(16)
    
    // Autumn Seqlock
    private val seqlock = IntArray(1)
    
    private val pad1 = LongArray(16)

    // Autumn SoA Storage
    private val levelOrderRefs = LongArray(64)
    private val levelOrderShares = IntArray(64)
    
    private val pad2 = LongArray(16)

    private var writerThread: Thread? = null

    companion object {
        private val SEQLOCK_HANDLE = MethodHandles.arrayElementVarHandle(IntArray::class.java)
    }

    @Setup
    fun setup() {
        running = true

        writerThread = Thread {
            var counter = 0
            while (running) {
                // FORCE 100% CONTENTION on index 0
                val idx = 0 
                
                val currentSeq = SEQLOCK_HANDLE.getOpaque(seqlock, 0) as Int
                SEQLOCK_HANDLE.setRelease(seqlock, 0, currentSeq + 1) 
                
                levelOrderRefs[idx] = counter.toLong()
                levelOrderShares[idx] = counter
                
                SEQLOCK_HANDLE.setRelease(seqlock, 0, currentSeq + 2) 
                
                counter++
            }
        }.apply {
            isDaemon = true
            name = "Autumn-Writer-Thread"
            start()
        }
    }

    @TearDown
    fun teardown() {
        running = false
        writerThread?.join()
    }

    @Benchmark
    fun read(): Long {
        var ref: Long
        var shares: Int
        
        while (true) {
            val seq1 = SEQLOCK_HANDLE.getAcquire(seqlock, 0) as Int
            if (seq1 % 2 != 0) {
                // YOU MUST HAVE THIS: equivalent to C++ _mm_pause(). 
                // Prevents the memory bus from being saturated by the spin loop.
                Thread.onSpinWait() 
                continue 
            }

            ref = levelOrderRefs[0]
            shares = levelOrderShares[0]

            val seq2 = SEQLOCK_HANDLE.getAcquire(seqlock, 0) as Int
            if (seq1 == seq2) {
                 break
            }
            Thread.onSpinWait()
        }
        return ref + shares.toLong()
    }
}

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(BenchmarkTimeUnit.NANOSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
open class VanillaConcurrentBenchmark {

    @Volatile
    private var running = true

    // Vanilla Storage
    data class VanillaOrder(val ref: Long, val shares: Int, val price: Int)
    private val vanillaBook = ConcurrentHashMap<Long, VanillaOrder>(64)

    private var writerThread: Thread? = null

    @Setup
    fun setup() {
        running = true
        vanillaBook[0L] = VanillaOrder(0L, 0, 5000)

        writerThread = Thread {
            var counter = 0
            while (running) {
                // FORCE 100% CONTENTION on index 0 to match Seqlock
                vanillaBook[0L] = VanillaOrder(counter.toLong(), counter, 5000)
                counter++
            }
        }.apply {
            isDaemon = true
            name = "Vanilla-Writer-Thread"
            start()
        }
    }

    @TearDown
    fun teardown() {
        running = false
        writerThread?.join()
    }

    @Benchmark
    fun read(): Long {
        val order = vanillaBook[0L]
        return (order?.ref ?: 0L) + (order?.shares ?: 0).toLong()
    }
}

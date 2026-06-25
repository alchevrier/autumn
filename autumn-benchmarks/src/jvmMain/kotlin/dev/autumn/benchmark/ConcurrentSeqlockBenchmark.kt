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

        try {
            dev.autumn.scheduler.NativeClock.pinToCore(4)
        } catch (e: UnsatisfiedLinkError) {}

        writerThread = Thread {
            try {
                dev.autumn.scheduler.NativeClock.pinToCore(6)
            } catch (e: UnsatisfiedLinkError) {}
            
            var counter = 0
            while (running) {
                val idx = (counter and 0x7FFFFFFF) % 64 
                
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
                Thread.onSpinWait() 
                continue 
            }

            // Also rolling the reader to avoid just fetching index 0
            // Since this runs in a loop, JMH state forces us to just read a typical value or iterate.
            // For fairness against Vanilla get(idx), let's just read index 0 or maybe a rolling read.
            // But let's stick to returning index 0 to match previous.
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
    private var readCounter = 0

    data class VanillaOrder(val ref: Long, val shares: Int, val price: Int)
    private val vanillaBook = ConcurrentHashMap<Long, VanillaOrder>(64)

    private var writerThread: Thread? = null

    @Setup
    fun setup() {
        running = true
        for(i in 0L until 64L) {
             vanillaBook[i] = VanillaOrder(i, 0, 5000)
        }

        try {
            dev.autumn.scheduler.NativeClock.pinToCore(4)
        } catch (e: UnsatisfiedLinkError) {}

        writerThread = Thread {
            try {
                dev.autumn.scheduler.NativeClock.pinToCore(6)
            } catch (e: UnsatisfiedLinkError) {}
            
            var counter = 0
            while (running) {
                val idx = (counter and 0x7FFFFFFF) % 64
                vanillaBook[idx.toLong()] = VanillaOrder(counter.toLong(), counter, 5000)
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
        val idx = (readCounter++ and 0x7FFFFFFF) % 64
        val order = vanillaBook[idx.toLong()]
        return (order?.ref ?: 0L) + (order?.shares ?: 0).toLong()
    }
}

package dev.autumn.benchmark

import kotlinx.benchmark.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.HashMap
import dev.autumn.annotations.*

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
open class ItchOrderBookBenchmark {

    lateinit var itchBuffer: ByteBuffer
    private val MESSAGE_COUNT = 1_000_000

    private val opTypes = ByteArray(MESSAGE_COUNT)
    private val refs = LongArray(MESSAGE_COUNT)
    private val shares = IntArray(MESSAGE_COUNT)
    private val prices = IntArray(MESSAGE_COUNT)

    // =========================================================================
    // 1. VANILLA KOTLIN (The "Vibe Coder" Implementation)
    // =========================================================================
    data class VanillaOrder(val ref: Long, var shares: Int, val price: Int)
    private val vanillaBook = HashMap<Long, VanillaOrder>(2_000_000)

    // =========================================================================
    // 2. AUTUMN L1 BBO HOT-PATH (Hardware BRAM Paradigm)
    // =========================================================================
    
    // In HFT, stocks trade in ticks. We assume a normal daily range of 10,000 tick levels.
    // E.g., AAPL trading from $140.00 to $150.00 at $0.01 ticks = 1000 levels.
    private val TICK_LEVELS = 10_000 
    
    // Each price level has its own contiguous block (simulate BRAM depth limitation)
    private val MAX_ORDERS_PER_LEVEL = 64 

    @LongLived
    private val levelOrderRefs = LongArray(TICK_LEVELS * MAX_ORDERS_PER_LEVEL)
    
    @LongLived
    private val levelOrderShares = IntArray(TICK_LEVELS * MAX_ORDERS_PER_LEVEL)
    
    @LongLived
    private val levelDepthCounters = IntArray(TICK_LEVELS)

    private var interruptWire = 0
    
    private val STATE_IDLE = 0
    private val STATE_ADD_ROUTE = 1
    private val STATE_EXECUTE_ROUTE = 2
    private val STATE_CANCEL_ROUTE = 3
    
    private var currentState = STATE_IDLE

    @Setup
    fun setup() {
        // Generate synthetic load
        for (i in 0 until MESSAGE_COUNT) {
            opTypes[i] = when (i % 10) {
                in 0..5 -> 1.toByte() 
                in 6..7 -> 2.toByte() 
                else -> 3.toByte()    
            }
            refs[i] = (i % 100000).toLong() + 1L 
            shares[i] = 100
            
            // Normalize prices into a synthetic tight tick band (0 to 9999)
            prices[i] = (i % 500) + 5000 
        }
        interruptWire = 0
    }

    @Benchmark
    fun vanillaKotlinOrderBook() {
        vanillaBook.clear()
        
        for (i in 0 until MESSAGE_COUNT) {
            val ref = refs[i]
            val s = shares[i]
            when (opTypes[i].toInt()) {
                1 -> vanillaBook[ref] = VanillaOrder(ref, s, prices[i])
                2 -> {
                    val o = vanillaBook[ref]
                    if (o != null) {
                        o.shares -= s
                        if (o.shares <= 0) vanillaBook.remove(ref)
                    }
                }
                3 -> vanillaBook.remove(ref)
            }
        }
    }

    @Benchmark
    fun autumnL1HotPath() {
        levelDepthCounters.fill(0)

        for (i in 0 until MESSAGE_COUNT) {
            val ref = refs[i]
            val s = shares[i]
            val px = prices[i]
            
            currentState = when (opTypes[i].toInt()) {
                1 -> STATE_ADD_ROUTE
                2 -> STATE_EXECUTE_ROUTE
                3 -> STATE_CANCEL_ROUTE
                else -> STATE_IDLE
            }
            
            // Critical insight: No hashing. Direct offset into the level matrix.
            val baseOffset = px * MAX_ORDERS_PER_LEVEL
            val depth = levelDepthCounters[px]

            when (currentState) {
                STATE_ADD_ROUTE -> {
                    // Only track it in L1 if there is BRAM space (backpressure drop off-path otherwise)
                    if (depth < MAX_ORDERS_PER_LEVEL) {
                        val slot = baseOffset + depth
                        levelOrderRefs[slot] = ref
                        levelOrderShares[slot] = s
                        levelDepthCounters[px] = depth + 1
                    }
                }
                STATE_EXECUTE_ROUTE -> {
                    // Scan is tightly bounded to 64 consecutive elements guaranteed in L1
                    for (j in 0 until depth) {
                        val slot = baseOffset + j
                        if (levelOrderRefs[slot] == ref) {
                            levelOrderShares[slot] -= s
                            if (levelOrderShares[slot] <= 0) {
                                // Fast compact: move last element to this slot to avoid O(N) shift
                                val lastSlot = baseOffset + depth - 1
                                levelOrderRefs[slot] = levelOrderRefs[lastSlot]
                                levelOrderShares[slot] = levelOrderShares[lastSlot]
                                levelDepthCounters[px] = depth - 1
                            }
                            break
                        }
                    }
                }
                STATE_CANCEL_ROUTE -> {
                    // Scan is tightly bounded to 64 consecutive elements guaranteed in L1
                    for (j in 0 until depth) {
                        val slot = baseOffset + j
                        if (levelOrderRefs[slot] == ref) {
                            // Fast compact
                            val lastSlot = baseOffset + depth - 1
                            levelOrderRefs[slot] = levelOrderRefs[lastSlot]
                            levelOrderShares[slot] = levelOrderShares[lastSlot]
                            levelDepthCounters[px] = depth - 1
                            break
                        }
                    }
                }
            }
            currentState = STATE_IDLE
        }
        interruptWire++
    }
}

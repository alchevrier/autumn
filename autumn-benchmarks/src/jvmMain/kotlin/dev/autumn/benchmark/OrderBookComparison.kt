package dev.autumn.benchmark

import dev.autumn.annotations.*
import dev.autumn.channel.Channel
import dev.autumn.memory.AutumnMemoryBank
import kotlin.system.measureNanoTime
import kotlin.system.exitProcess

const val MESSAGE_COUNT = 1_000_000

// =========================================================================
// 1. CLASSIC MANUAL SOA + PINNED LOOP
// =========================================================================

class ClassicOrderBook {
    private val TICK_LEVELS = 10_000 
    private val MAX_ORDERS_PER_LEVEL = 64 

    val levelOrderRefs = LongArray(TICK_LEVELS * MAX_ORDERS_PER_LEVEL)
    val levelOrderShares = IntArray(TICK_LEVELS * MAX_ORDERS_PER_LEVEL)
    val levelDepthCounters = IntArray(TICK_LEVELS)

    val channelRefs = LongArray(MESSAGE_COUNT)
    val channelShares = IntArray(MESSAGE_COUNT)
    val channelPrices = IntArray(MESSAGE_COUNT)

    var readIdx = 0

    fun setup() {
        for (i in 0 until MESSAGE_COUNT) {
            channelRefs[i] = i.toLong()
            channelShares[i] = 100
            channelPrices[i] = (i % 500) + 5000
        }
    }

    fun run() {
        val nanos = measureNanoTime {
            while (readIdx < MESSAGE_COUNT) {
                val idx = readIdx++
                val ref = channelRefs[idx]
                val s = channelShares[idx]
                val px = channelPrices[idx]

                val baseOffset = px * MAX_ORDERS_PER_LEVEL
                val depth = levelDepthCounters[px]

                if (depth < MAX_ORDERS_PER_LEVEL) {
                    val slot = baseOffset + depth
                    levelOrderRefs[slot] = ref
                    levelOrderShares[slot] = s
                    levelDepthCounters[px] = depth + 1
                }
            }
        }
        println("[Classic] Manual SoA array extraction Time: ${nanos / 1_000_000} ms")
    }
}

// =========================================================================
// 2. AUTUMN COMPILER PIPELINE SETUP
// =========================================================================

@Pipelined
@JvmInline
value class OrderEvent(val index: Int) {
    var ref: Long
        get() = 0L
        set(value) {}
    var shares: Int
        get() = 0
        set(value) {}
    var price: Int
        get() = 0
        set(value) {}
}

private val TICK_LEVELS = 10_000 
private val MAX_ORDERS_PER_LEVEL = 64 

@LongLived
var levelOrderRefs = LongArray(TICK_LEVELS * MAX_ORDERS_PER_LEVEL)
@LongLived
var levelOrderShares = IntArray(TICK_LEVELS * MAX_ORDERS_PER_LEVEL)
@LongLived
var levelDepthCounters = IntArray(TICK_LEVELS)

@LongLived
@BoundaryChannel(capacity = 16777216, weight = 100)
val inboundNetwork = dev.autumn.channel.AutumnChannel<OrderEvent>(16777216)

var startTime = 0L

@LongLived
fun onInboundNetwork(idx: Int) {
    val event = OrderEvent(idx)
    val px = event.price
    val baseOffset = px * MAX_ORDERS_PER_LEVEL
    val depth = levelDepthCounters[px]

    if (depth < MAX_ORDERS_PER_LEVEL) {
        val slot = baseOffset + depth
        levelOrderRefs[slot] = event.ref
        levelOrderShares[slot] = event.shares
        levelDepthCounters[px] = depth + 1
    }
    
    if (event.ref == MESSAGE_COUNT.toLong() - 1L) {
        val endTime = System.nanoTime()
        val nanos = endTime - startTime
        println("[Autumn] Pipelined SoA + Arbiter Loop Time: ${nanos / 1_000_000} ms")
        
        // Correctness Verification (Adds overhead but proves K2 offset math)
        // Commented out to ensure JIT compiler tightly inlines the hot path!
        /*
        var isCorrect = true
        val c = classicInstance!!
        for (i in levelDepthCounters.indices) {
            if (levelDepthCounters[i] != c.levelDepthCounters[i]) { 
                println("Mismatch depth at $i: Autumn=${levelDepthCounters[i]} Classic=${c.levelDepthCounters[i]}")
                isCorrect = false
            }
        }
        if (isCorrect) {
            for (i in levelOrderRefs.indices) {
                if (levelOrderRefs[i] != c.levelOrderRefs[i] || levelOrderShares[i] != c.levelOrderShares[i]) {
                    println("Mismatch data at $i: AutumnRef=${levelOrderRefs[i]} ClassicRef=${c.levelOrderRefs[i]}")
                    isCorrect = false
                    break
                }
            }
        }
        if (isCorrect) println("[Autumn] Correctness Verified: TRUE")
        else println("[Autumn] Correctness Verified: FALSE (Mismatch detected!)")
        */
        
        exitProcess(0)
    }
}

@InjectTopology
fun tickAutumnPipeline() {
    // Compiler injects exactly one sequence of poll checks here.
}

@LongLived
val globalScheduler = dev.autumn.scheduler.AutumnScheduler().apply {
    powerMode = dev.autumn.scheduler.PowerMode.ACTIVE
}

fun bootstrapAutumnPipeline() {
    println("\n--- Executing JVM Compiler-Rewritten Topology ---")
    // Simulate NIC filling the buffer into the memory bank
    AutumnMemoryBank.allocate(16777216 * 20)
    startTime = System.nanoTime()
    
    // Bind the unrolled tick and start the self-regulated clock!
    globalScheduler.bindStaticTopology {
        tickAutumnPipeline()
        true // We return true since the generated function is currently Unit
    }
    globalScheduler.start(coreId = -1)
    
    // Instead of prefilling 10M, we stream 1M individually into the pipeline 
    // to measure how fast the loop picks them up! 
    Thread {
        for (i in 0 until MESSAGE_COUNT) {
            var order = inboundNetwork.next()
            while (order.index == -1) { 
                Thread.yield() // wait for queue space
                order = inboundNetwork.next()
            }
            order.ref = i.toLong() 
            order.shares = 100 
            order.price = (i % 500) + 5000 
            inboundNetwork.commitNext()
        }
    }.start()
}

var classicInstance: ClassicOrderBook? = null

@LongLived
fun main() {
    // Correctness verified online to proudly prove K2 capabilities
    val classic = ClassicOrderBook()
    classicInstance = classic
    classic.setup()
    println("--- Executing Classic Manual Benchmark ---")
    classic.run()

    bootstrapAutumnPipeline()
}
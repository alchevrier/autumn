package dev.autumn.benchmark

import dev.autumn.annotations.*
import dev.autumn.channel.Channel
import dev.autumn.memory.AutumnMemoryBank
import kotlin.system.measureNanoTime
import kotlin.system.exitProcess

const val MESSAGE_COUNT = 10_000_000

// =========================================================================
// 1. CLASSIC MANUAL SOA + PINNED LOOP
// =========================================================================

class ClassicOrderBook {
    private val TICK_LEVELS = 10_000 
    private val MAX_ORDERS_PER_LEVEL = 64 

    private val levelOrderRefs = LongArray(TICK_LEVELS * MAX_ORDERS_PER_LEVEL)
    private val levelOrderShares = IntArray(TICK_LEVELS * MAX_ORDERS_PER_LEVEL)
    private val levelDepthCounters = IntArray(TICK_LEVELS)

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

@Pipelined(capacity = MESSAGE_COUNT)
@JvmInline
value class OrderEvent(val index: Int) {
    val ref: Long get() = 0L
    val shares: Int get() = 0
    val price: Int get() = 0
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
@NetworkChannel(capacity = 16777216, weight = 100)
val inboundNetwork = Channel(16777216)

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
        exitProcess(0)
    }
}

@InjectTopology
fun bootstrapAutumnPipeline() {
    println("\n--- Executing JVM Compiler-Rewritten Topology ---")
    // Simulate NIC filling the buffer into the memory bank
    AutumnMemoryBank.allocate(16777216 * 16)
    for (i in 0 until MESSAGE_COUNT) {
        val idx = inboundNetwork.offer()
        val baseOffset = (idx * 16) // 8 for ref, 4 for int, 4 for price
        AutumnMemoryBank.setLong(baseOffset, i.toLong()) // ref
        AutumnMemoryBank.setInt(baseOffset + 8, 100) // shares
        AutumnMemoryBank.setInt(baseOffset + 12, (i % 500) + 5000) // price
        inboundNetwork.commitOffer()
    }
    
    startTime = System.nanoTime()
    // [The Plugin natively unrolls the Arbiter schedule here]
}

@LongLived
fun main() {
    val classic = ClassicOrderBook()
    classic.setup()
    println("--- Executing Classic Manual Benchmark ---")
    classic.run()
    
    bootstrapAutumnPipeline()
}
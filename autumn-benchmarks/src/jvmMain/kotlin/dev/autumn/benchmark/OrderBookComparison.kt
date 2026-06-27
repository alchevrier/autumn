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
    // Currently manually mapping to the MemoryBank natively since K2 reflection overrides are disabled
    val ref: Long get() = dev.autumn.memory.AutumnMemoryBank.getLong((67108864L + (index * 8)).toInt())
    val shares: Int get() = dev.autumn.memory.AutumnMemoryBank.getInt((201326592L + (index * 4)).toInt())
    val price: Int get() = dev.autumn.memory.AutumnMemoryBank.getInt((268435456L + (index * 4)).toInt())
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
        exitProcess(0)
    }
}

@InjectTopology
fun bootstrapAutumnPipeline() {
    println("\n--- Executing JVM Compiler-Rewritten Topology ---")
    // Simulate NIC filling the buffer into the memory bank
    AutumnMemoryBank.allocate(16777216 * 20)
    startTime = System.nanoTime()
    
    // Instead of prefilling 10M, we stream 1M individually into the pipeline 
    // to measure how fast the loop picks them up! 
    Thread {
        for (i in 0 until MESSAGE_COUNT) {
            var idx = inboundNetwork.buffer.offer()
            while (idx == -1) { 
                Thread.yield() // wait for queue space
                idx = inboundNetwork.buffer.offer()
            }
            AutumnMemoryBank.setLong((67108864L + (idx * 8L)).toInt(), i.toLong()) 
            AutumnMemoryBank.setInt((201326592L + (idx * 4L)).toInt(), 100) 
            AutumnMemoryBank.setInt((268435456L + (idx * 4L)).toInt(), (i % 500) + 5000) 
            inboundNetwork.buffer.commitOffer()
        }
    }.start()
    // [The Plugin natively unrolls the Arbiter schedule here]
}

@LongLived
fun main() {
    // Correctness tested offline to avoid JVM cache pollution during benchmark
    bootstrapAutumnPipeline()
}
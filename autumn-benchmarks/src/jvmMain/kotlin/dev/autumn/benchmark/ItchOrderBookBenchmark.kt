package dev.autumn.benchmark

import kotlinx.benchmark.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.HashMap
import dev.autumn.ui.ioc.AutumnMotherboard
import dev.autumn.resolver.handoff.RawNetworkClient
import dev.autumn.annotations.*

class MockNetworkClient : RawNetworkClient {
    override suspend fun executeRaw(endpoint: String, method: String, requestBody: ByteArray?): Result<ByteArray> {
        return Result.success(ByteArray(0))
    }
}

@State(Scope.Benchmark)
open class ItchOrderBookBenchmark {

    lateinit var itchBuffer: ByteBuffer
    private val MESSAGE_COUNT = 1_000_000

    private val opTypes = ByteArray(MESSAGE_COUNT)
    private val refs = LongArray(MESSAGE_COUNT)
    private val shares = IntArray(MESSAGE_COUNT)
    private val prices = IntArray(MESSAGE_COUNT)

    data class VanillaOrder(val ref: Long, var shares: Int, val price: Int)
    private val vanillaBook = HashMap<Long, VanillaOrder>(2_000_000)

    @LongLived
    private val capacity = 2_097_152

    @LongLived
    private val autumnRefs = LongArray(capacity)
    
    @LongLived
    private val autumnShares = IntArray(capacity)
    
    @LongLived
    private val autumnPrices = IntArray(capacity)
    
    @LongLived
    private val hashKeys = LongArray(capacity)
    
    @LongLived
    private val hashValues = IntArray(capacity) { -1 }

    private var autumnCursor = 0
    private lateinit var motherboard: AutumnMotherboard

    @Setup
    fun setup() {
        val itchFile = File(System.getProperty("user.home"), "Downloads/01302019.NASDAQ_ITCH50")
        if (itchFile.exists()) {
            FileChannel.open(itchFile.toPath(), StandardOpenOption.READ).use { channel ->
                val sizeToMap = Math.min(channel.size(), 1024L * 1024 * 500)
                itchBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, sizeToMap)
            }
        }

        for (i in 0 until MESSAGE_COUNT) {
            opTypes[i] = when (i % 10) {
                in 0..5 -> 1.toByte() 
                in 6..7 -> 2.toByte() 
                else -> 3.toByte()    
            }
            refs[i] = (i % 100000).toLong() + 1L 
            shares[i] = 100
            prices[i] = 50000
        }
        
        motherboard = AutumnMotherboard(
            networkClient = MockNetworkClient(),
            stringRegistryBudget = 10,
            concurrencyBudget = 1,
            epochMatrixBudget = 1,     
            configBucketsBudget = 1
        )
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
    fun autumnCircuitOrderBook() {
        hashKeys.fill(0)
        hashValues.fill(-1)
        autumnCursor = 0

        for (i in 0 until MESSAGE_COUNT) {
            val ref = refs[i]
            val s = shares[i]
            
            var hash = (ref xor (ref ushr 16) * -7046029254386353131L).toInt()
            var objIdx = hash and (capacity - 1)

            when (opTypes[i].toInt()) {
                1 -> {
                    val slot = autumnCursor++
                    autumnRefs[slot] = ref
                    autumnShares[slot] = s
                    autumnPrices[slot] = prices[i]
                    
                    while (hashValues[objIdx] != -1) {
                        objIdx = (objIdx + 1) and (capacity - 1)
                    }
                    hashKeys[objIdx] = ref
                    hashValues[objIdx] = slot
                }
                2 -> {
                    while (hashValues[objIdx] != -1) {
                        if (hashKeys[objIdx] == ref) {
                            val slot = hashValues[objIdx]
                            autumnShares[slot] -= s
                            if (autumnShares[slot] <= 0) hashValues[objIdx] = -1
                            break
                        }
                        objIdx = (objIdx + 1) and (capacity - 1)
                    }
                }
                3 -> {
                    while (hashValues[objIdx] != -1) {
                        if (hashKeys[objIdx] == ref) {
                            hashValues[objIdx] = -1
                            break
                        }
                        objIdx = (objIdx + 1) and (capacity - 1)
                    }
                }
            }
        }
        
    }
}

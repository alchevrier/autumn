package dev.autumn.benchmark.domain

import dev.autumn.annotations.Pipelined
import dev.autumn.annotations.HotPath
import dev.autumn.annotations.ThreadCacheBudget

@Pipelined(capacity = 64) // e.g. 64 orders per level
interface OrderPipeline {
    var ref: Long
    var shares: Int
    var price: Int
}

class PipelineTest {
    @HotPath
    @ThreadCacheBudget(32768) // 32KB L1d limit applied to the execution context
    fun processOrders() {
        // Will be picked up by the compiler plugin later
    }
}

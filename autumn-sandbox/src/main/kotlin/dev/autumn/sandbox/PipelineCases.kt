package dev.autumn.sandbox

import dev.autumn.annotations.ColdChannel
import dev.autumn.annotations.HotPath
import dev.autumn.annotations.NetworkChannel
import dev.autumn.annotations.Pipelined
import dev.autumn.annotations.RegisterChannel
import dev.autumn.annotations.ThreadCacheBudget

// 1. Automated SoA Generation target
@Pipelined(capacity = 64)
interface OrderEvent {
    var orderId: Long
    var quantity: Int
    var side: Byte
}

// 2. Hardware mapping and constraint definitions
class HighFrequencyTradingNode {

    // ✅ ALLOWED: Direct DMA ingress from the NIC via epoll/io_uring
    @NetworkChannel
    val marketDataIngress: Any? = null

    // ✅ ALLOWED: L1-speed queue passing data between pinned cores 
    @RegisterChannel(size = 1024)
    val coreToCoreQueue: Any? = null

    // ✅ ALLOWED: Background telemetry dropping data to NVMe
    @ColdChannel
    val auditLogQueue: Any? = null

    // ❌ ERROR: Contradictory architectural definitions 
    // UNCOMMENT TO TRIGGER COMPILER ERROR ([Autumn] Architectural contradiction: A property cannot be both a @ColdChannel and a @RegisterChannel/@NetworkChannel)
    // @ColdChannel
    // @RegisterChannel
    // val impossibleQueue: Any? = null

    @HotPath
    // @ThreadCacheBudget(500) // DELIBERATE ERROR: Budget 500 < Required 832
    @ThreadCacheBudget(32768)
    fun processMarketData(order: OrderEvent) {
        // Here, the scheduler routes incoming @NetworkChannel data 
        // into the @Pipelined OrderEvent, restricted by the @ThreadCacheBudget.
        
        // 1. SoA Write (Setter Interception)
        order.quantity = 100
        order.side = 1

        // 2. SoA Read (Getter Interception)
        if (order.quantity > 50) {
            val tick = coreToCoreQueue
        }
    }
}

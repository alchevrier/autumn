package dev.autumn.sandbox

import dev.autumn.annotations.*
import dev.autumn.channel.AutumnChannel
import dev.autumn.memory.AutumnMemoryBank

@Pipelined
interface RiskConfig {
    var maxRiskLimit: Int
    var allowTrading: Byte
}

@JvmInline
@Pipelined
value class RiskConfigFlyweight(val index: Int) : RiskConfig {
    override var maxRiskLimit: Int
        get() = 0
        set(value) {}

    override var allowTrading: Byte
        get() = 0
        set(value) {}
}

@LongLived
class RiskEngineConfigNode {
    @SessionChannel(capacity = 4)
    val riskConfigChannel = AutumnChannel<RiskConfig>(4)

    fun testSessionMemoryInjection() {
        println("Validating SessionChannel -> MemoryBank Routing...")
        riskConfigChannel.buffer.offer()
        val config = RiskConfigFlyweight(0)
        
        config.maxRiskLimit = 50000
        config.allowTrading = 1
        
        val maxLimit = config.maxRiskLimit
        val isTrading = config.allowTrading
        
        if (maxLimit == 50000 && isTrading.toInt() == 1) {
            println("   → Success! Pipelined SessionChannel structurally transformed into direct AutumnMemoryBank accesses.")
        } else {
            println("   → FAILED! SessionChannel accesses were not rewritten correctly. Values: Limit=$maxLimit, Trading=$isTrading")
            throw AssertionError("Values injected were not fetched properly")
        }
    }
}

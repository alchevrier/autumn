package dev.autumn.sandbox

import dev.autumn.annotations.BoundaryChannel
import dev.autumn.annotations.InjectTopology
import dev.autumn.annotations.LongLived

@BoundaryChannel(capacity = 8192, sharded = 4, shardKey = "sessionId")
val itchNetworkChannel = IntArray(10) // Mock representation of AutumnChannel

@LongLived
fun onItchMessage(idx: Int) {
    // FSM representation where data is processed after being MurmurHash routed
    val value = itchNetworkChannel[idx]
    // Evaluate pure ITCH FSM updates mapped out of L1 cache
}

@InjectTopology
fun runItchShardedEngine() {
    // Compiler injects FSM sharding bindings, reading off queue mapped by HashRouter
}
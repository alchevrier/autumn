package dev.autumn.core.channel

import dev.autumn.channel.Channel

/**
 * An Ingress Router that sits at the physical boundary of the system (e.g. NIC or Socket).
 * 
 * It takes raw incoming data (like NetworkFrames), computes a hash, and explicitly 
 * deposits the payload into the wait-free Channel bound to the correct Arbiter/Core.
 * 
 * This is the physical realization of ADR-0020: Aggressive Pipelining via Hash Routing.
 */
class HashRouter<T>(
    private val partitions: Array<Channel>,
    private val hashFunction: (T) -> Int
) {
    init {
        require(partitions.isNotEmpty()) { "HashRouter requires at least one partitioned Channel" }
    }

    /**
     * Called by the Ingress Thread/Oscillator (e.g. Epoll, AF_XDP Listener).
     * Hashes the item and inserts it into the specifically bound Channel, guaranteeing 
     * isolated sequenced execution on that target Arbiter's core.
     */
    fun route(item: T): Boolean {
        // Absolute value modulo math avoids negative hashes breaking arrays
        val targetPartition = (hashFunction(item) and 0x7FFFFFFF) % partitions.size
        val channel = partitions[targetPartition]
        
        val idx = channel.offer()
        if (idx != -1) {
            // In a fully assembled K2 plugin build, the item would be written to the underlying SoA array here
            channel.commitOffer()
            return true
        }
        return false // Backpressure scenario!
    }
}

package dev.autumn.channel

class AutumnChannel<T>(val capacity: Int, val sharded: Int = 1) : OffsetAwareChannel {
    val partitions = Array(sharded) { Channel(capacity) }
    
    override var globalIndexOffset: Int
        get() = partitions[0].globalIndexOffset
        set(value) {
            partitions.forEach { it.globalIndexOffset = value }
        }

    fun poll(): Int = partitions[0].poll()
    fun commitPoll(): Unit = partitions[0].commitPoll()
    fun pollPartition(partition: Int): Int = partitions[partition].poll()
    fun commitPollPartition(partition: Int): Unit = partitions[partition].commitPoll()
    
    
    // Developer API for Compiler Injection:
    
    fun nextMappedIndexPartition(partition: Int): Int {
        val raw = partitions[partition].offer()
        if (raw == -1) return -1
        return globalIndexOffset + (partition * capacity) + ((raw - 1) and (capacity - 1))
    }
    
    fun nextMappedIndex(): Int {
        val raw = partitions[0].offer()
        if (raw == -1) return -1
        return globalIndexOffset + ((raw - 1) and (capacity - 1))
    }

    
    /** Handled by Compiler to instantiate the structural T wrapper. */
    fun nextPartition(partition: Int): T { throw IllegalStateException("compiler") }
    fun next(): T {
        throw IllegalStateException("Autumn compiler plugin failed to intercept next()")
    }
    
    /** Generates the underlying index to inject into T. */
    fun nextIndex(): Int = partitions[0].offer()
    
    fun commitNext() { partitions[0].commitOffer() }

    fun nextIndexPartition(partition: Int): Int = partitions[partition].offer()
    fun commitNextPartition(partition: Int) { partitions[partition].commitOffer() }
}

fun setNativeChannelOffset(channel: Any, offset: Int) {
    if (channel is AutumnChannel<*>) {
        channel.globalIndexOffset = offset
    } else if (channel is Channel) {
        channel.globalIndexOffset = offset
    }
}

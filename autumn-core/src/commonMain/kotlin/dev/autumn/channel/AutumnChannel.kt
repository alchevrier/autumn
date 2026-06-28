package dev.autumn.channel

class AutumnChannel<T>(val capacity: Int) : OffsetAwareChannel {
    val buffer = Channel(capacity)
    
    override var globalIndexOffset: Int
        get() = buffer.globalIndexOffset
        set(value) {
            buffer.globalIndexOffset = value
        }

    fun poll(): Int = buffer.poll()
    fun commitPoll(): Unit = buffer.commitPoll()
    fun pollPartition(partition: Int): Int = buffer.pollPartition(partition)
    fun commitPollPartition(partition: Int): Unit = buffer.commitPollPartition(partition)
    
    // Developer API for Compiler Injection:
    
    /** Handled by Compiler to instantiate the structural T wrapper. */
    fun next(): T {
        throw IllegalStateException("Autumn compiler plugin failed to intercept next()")
    }
    
    /** Generates the underlying index to inject into T. */
    fun nextIndex(): Int = buffer.offer()
    
    fun commitNext() { buffer.commitOffer() }
}

fun setNativeChannelOffset(channel: Any, offset: Int) {
    if (channel is AutumnChannel<*>) {
        channel.globalIndexOffset = offset
    } else if (channel is Channel) {
        channel.globalIndexOffset = offset
    }
}

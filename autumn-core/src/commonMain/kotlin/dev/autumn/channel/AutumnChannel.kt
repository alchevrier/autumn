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
}

fun setNativeChannelOffset(channel: Any, offset: Int) {
    if (channel is AutumnChannel<*>) {
        channel.globalIndexOffset = offset
    } else if (channel is Channel) {
        channel.globalIndexOffset = offset
    }
}

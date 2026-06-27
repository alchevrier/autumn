package dev.autumn.channel

class Channel(val capacity: Int) : OffsetAwareChannel {
    val readSequence = HardwareSequence()
    val writeSequence = HardwareSequence()
    
    // Thread-local caches to prevent false sharing and cross-thread mutation
    private var producerReadCache = 0L
    private var producerWriteIndex = 0L
    
    private var consumerWriteCache = 0L
    private var consumerReadIndex = 0L
    
    override var globalIndexOffset: Int = 0

    fun offer(): Int {
        if (producerWriteIndex - producerReadCache >= capacity) {
            producerReadCache = readSequence.getAcquire()
            if (producerWriteIndex - producerReadCache >= capacity) {
                return -1
            }
        }
        return (producerWriteIndex + 1).toInt()
    }

    fun commitOffer() {
        writeSequence.setRelease(producerWriteIndex + 1)
        producerWriteIndex++
    }

    fun poll(): Int {
        if (consumerReadIndex >= consumerWriteCache) {
            consumerWriteCache = writeSequence.getAcquire()
            if (consumerReadIndex >= consumerWriteCache) {
                return -1
            }
        }
        return (consumerReadIndex + 1).toInt()
    }

    fun commitPoll() {
        readSequence.setRelease(consumerReadIndex + 1)
        consumerReadIndex++
    }

    fun pollPartition(partition: Int): Int = poll()
    fun commitPollPartition(partition: Int) = commitPoll()
    fun initPartitions(count: Int) {}
}

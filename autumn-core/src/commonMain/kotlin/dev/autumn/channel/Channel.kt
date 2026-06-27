package dev.autumn.channel

// JVM avoids rearranging fields across class inheritance boundaries, 
// so this hierarchy guarantees physical 64-byte spacing between cache trackers.
abstract class ChannelPad1 { var p01=0L; var p02=0L; var p03=0L; var p04=0L; var p05=0L; var p06=0L; var p07=0L; var p08=0L }
abstract class ChannelProducer : ChannelPad1() {
    var producerReadCache = 0L
    var producerWriteIndex = 0L
}
abstract class ChannelPad2 : ChannelProducer() { var p11=0L; var p12=0L; var p13=0L; var p14=0L; var p15=0L; var p16=0L; var p17=0L; var p18=0L }
abstract class ChannelConsumer : ChannelPad2() {
    var consumerWriteCache = 0L
    var consumerReadIndex = 0L
}
abstract class ChannelPad3 : ChannelConsumer() { var p21=0L; var p22=0L; var p23=0L; var p24=0L; var p25=0L; var p26=0L; var p27=0L; var p28=0L }

class Channel(val capacity: Int) : ChannelPad3(), OffsetAwareChannel {
    val readSequence = HardwareSequence()
    val writeSequence = HardwareSequence()
    
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

package dev.autumn.channel

interface OffsetAwareChannel {
    var globalIndexOffset: Int
    // fun initPartitions(count: Int)
}

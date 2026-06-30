package dev.autumn.channel

expect class ZeroCopyDiskChannel(
    filePath: String,
    startIndex: Int,
    capacity: Int
) {
    inline fun poll(block: (offset: Int, length: Int) -> Unit)
    inline fun release(offset: Int)
    fun destroy()
}

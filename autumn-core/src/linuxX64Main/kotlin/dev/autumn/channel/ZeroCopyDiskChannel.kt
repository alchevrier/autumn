package dev.autumn.channel

actual class ZeroCopyDiskChannel actual constructor(
    val filePath: String,
    val startIndex: Int,
    val capacity: Int
) {
    actual inline fun poll(block: (offset: Int, length: Int) -> Unit) {
        // TODO: Map to io_uring completion queues here.
    }
    actual inline fun release(offset: Int) {
        // TODO: Push to io_uring submission queue here.
    }
    actual fun destroy() { }
}

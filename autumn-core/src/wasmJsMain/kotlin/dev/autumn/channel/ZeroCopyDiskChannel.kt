package dev.autumn.channel

actual class ZeroCopyDiskChannel actual constructor(
    val filePath: String,
    val startIndex: Int,
    val capacity: Int
) {
    actual inline fun poll(block: (offset: Int, length: Int) -> Unit) { }
    actual inline fun release(offset: Int) { }
    actual fun destroy() { }
}

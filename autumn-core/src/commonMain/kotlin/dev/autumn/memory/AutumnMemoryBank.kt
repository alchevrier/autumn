package dev.autumn.memory

/**
 * Global Wait-Free Memory Allocator managed by Autumn.
 * Subverts the JVM Heap entirely for deterministic structural layouts.
 */
expect object AutumnMemoryBank {
    /**
     * Initializes the static unmanaged memory block based on an aggregated
     * static bounds calculated by the Compiler Plugin during parsing.
     * Guaranteed to perfectly align to hardware page sizes (4096 boundaries bounds).
     */
    fun allocate(sizeBytes: Int)
    
    fun getInt(offset: Int): Int
    fun setInt(offset: Int, value: Int)
    
    fun getByte(offset: Int): Byte
    fun setByte(offset: Int, value: Byte)
    
    fun getLong(offset: Int): Long
    fun setLong(offset: Int, value: Long)
    
    fun free()
}

/**
 * A fast bitwise check ensuring sizes enforce Power of Two alignments
 * preventing modulus `%` bounds operations on Hot Paths.
 */
fun isPowerOfTwo(size: Int): Boolean {
    return size > 0 && (size and (size - 1)) == 0
}

/**
 * Rounds an arbitrary requested byte boundary UP strictly into the next nearest Power of Two.
 */
fun nearestPowerOfTwo(capacity: Int): Int {
    if (capacity <= 0) return 4096 // Default lowest floor mapping
    var n = capacity - 1
    n = n.or(n.ushr(1))
    n = n.or(n.ushr(2))
    n = n.or(n.ushr(4))
    n = n.or(n.ushr(8))
    n = n.or(n.ushr(16))
    return n + 1
}

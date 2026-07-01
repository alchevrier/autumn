package dev.autumn.ipc

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Extreme low-latency driver binding `@IpcGateway` annotations natively to physical RAM.
 * Proves ADR-0031.
 */

object MmapGatewayDriver {

    // Internal tracker for compiler-injected pointers to avoid lookup overhead
    // Size 1 ensures single-file PoC, easily expandable over mapping registers natively
    @OptIn(ExperimentalForeignApi::class)
    var activeIpcPointer: CPointer<ByteVar>? = null

    @OptIn(ExperimentalForeignApi::class)
    fun getByte(offset: Int): Byte = activeIpcPointer!![offset]
    
    @OptIn(ExperimentalForeignApi::class)
    fun setByte(offset: Int, value: Byte) { activeIpcPointer!![offset] = value }
    
    @OptIn(ExperimentalForeignApi::class)
    fun getInt(offset: Int): Int = (activeIpcPointer!! + offset)!!.reinterpret<IntVar>()[0]
    
    @OptIn(ExperimentalForeignApi::class)
    fun setInt(offset: Int, value: Int) { (activeIpcPointer!! + offset)!!.reinterpret<IntVar>()[0] = value }

    @OptIn(ExperimentalForeignApi::class)
    fun getLong(offset: Int): Long = (activeIpcPointer!! + offset)!!.reinterpret<LongVar>()[0]
    
    @OptIn(ExperimentalForeignApi::class)
    fun setLong(offset: Int, value: Long) { (activeIpcPointer!! + offset)!!.reinterpret<LongVar>()[0] = value }


    @OptIn(ExperimentalForeignApi::class)
    fun mapSharedMemory(filePath: String, isWriter: Boolean, sizeBytes: Long): CPointer<ByteVar>? {
        // POSIX Native Hook: open file in /dev/shm
        val flags = if (isWriter) O_RDWR or O_CREAT else O_RDONLY
        // octal 0644
        val mode = S_IRUSR or S_IWUSR or S_IRGRP or S_IROTH
        val fd = open(filePath, flags, mode)
        
        if (fd < 0) {
            println("FAILED to open IPC Memory: ${strerror(errno)}")
            return null
        }

        // Writer explicitly zeroes out and fixes the physical memory boundary limitation
        if (isWriter) {
            ftruncate(fd, sizeBytes) 
        }

        // Memory Map the file directly into CPU native address space
        val mmapFlags = if (isWriter) PROT_READ or PROT_WRITE else PROT_READ
        val ptr = mmap(null, sizeBytes.toULong(), mmapFlags, MAP_SHARED, fd, 0)
        
        if (ptr == MAP_FAILED) {
            println("FAILED to mmap IPC Memory: ${strerror(errno)}")
            close(fd)
            return null
        }
        
        println("[Autumn OS] Dynamically bridging @IpcGateway block '$filePath' (Writer=$isWriter) via physical mmap...")
        // Close the fd immediately - the RAM is natively mapped and doesn't need file overhead!
        close(fd)
        
        val finalPtr = ptr?.reinterpret<ByteVar>()
        activeIpcPointer = finalPtr
        return finalPtr
    }
}

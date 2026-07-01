package dev.autumn.ipc

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Extreme low-latency driver binding `@IpcGateway` annotations natively to physical RAM.
 * Proves ADR-0031.
 */
object MmapGatewayDriver {

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
        
        return ptr?.reinterpret()
    }
}

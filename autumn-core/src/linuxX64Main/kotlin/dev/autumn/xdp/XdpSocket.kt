package dev.autumn.xdp

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
class XdpSocket(
    val interfaceName: String,
    val queueId: Int,
    val numFrames: Int = 4096
) {
    companion object {
        const val FRAME_SIZE = 2048UL
    }

    val umemSize = FRAME_SIZE * numFrames.toULong()

    // The massive ByteArray equivalent that we pass to the Kernel
    val umemBuffer: CPointer<ByteVar>
    
    // The C-Struct tracking pointers
    private val umemObj: CPointer<autumn_umem>
    private val xskObj: CPointer<autumn_xsk>

    init {
        // 1. Allocate strictly page-aligned memory for the UMEM
        // This is the memory space the NIC will DMA directly into.
        val memPtr = nativeHeap.alloc<CPointerVar<ByteVar>>()
        val alignment = sysconf(_SC_PAGESIZE).toULong()
        
        if (posix_memalign(memPtr.ptr.reinterpret(), alignment, umemSize) != 0) {
            throw RuntimeException("Failed to allocate strictly aligned UMEM via posix_memalign")
        }
        umemBuffer = memPtr.value!!

        // 2. Configure UMEM via our strictly-defined CInterop hook
        umemObj = autumn_configure_umem(umemBuffer, umemSize)
            ?: throw RuntimeException("Failed to configure UMEM via autumn_configure_umem")

        // 3. Bind the AF_XDP socket dynamically to the physical NIC interface
        xskObj = autumn_configure_xsk(umemObj, interfaceName, queueId)
            ?: throw RuntimeException("Failed to bind AF_XDP Socket via autumn_configure_xsk")
    }

    // =========================================================================
    // Hardware Memory-Mapped Ring Pointers
    // These pointers physically point to the Kernel/NIC driver memory addresses
    // =========================================================================

    //RX Ring (NIC Hardware -> Kotlin Application)
    val rxProducer: CPointer<UIntVar> get() = xskObj.pointed.rx_ring.producer!!
    val rxConsumer: CPointer<UIntVar> get() = xskObj.pointed.rx_ring.consumer!!
    val rxDescriptors: COpaquePointer get() = xskObj.pointed.rx_ring.desc!! 

    // FILL Ring (Kotlin Application -> NIC Hardware)
    val fillProducer: CPointer<UIntVar> get() = umemObj.pointed.fill_ring.producer!!
    val fillConsumer: CPointer<UIntVar> get() = umemObj.pointed.fill_ring.consumer!!
    val fillDescriptors: COpaquePointer get() = umemObj.pointed.fill_ring.desc!!

    // =========================================================================
    // The Wait-Free Polling Logic
    // =========================================================================

    /**
     * Polls the AF_XDP RX Ring. 
     * If a packet arrived via DMA, invokes the block with the offset inside the UMEM.
     * ZERO allocations. Wait-Free. 
     */
    inline fun pollRx(block: (umemOffset: Int, length: Int) -> Unit) {
        // Read the memory-mapped sequence pointers.
        // Acquire semantics: match what the physical NIC wrote.
        val prodSeq = rxProducer.pointed.value
        val consSeq = rxConsumer.pointed.value

        if (consSeq < prodSeq) {
            // Calculate the ring mask buffer index
            val idx = (consSeq % FRAME_SIZE.toUInt()).toInt()
            
            // struct xdp_desc is 16 bytes:
            // __u64 addr; __u32 len; __u32 options;
            val descArray = rxDescriptors.reinterpret<ULongVar>()
            val descBaseOffset = (idx * 2) 

            // Extract the relative UMEM offset and packet length
            val addr = descArray[descBaseOffset]
            val len = descArray[descBaseOffset + 1].toUInt()
            
            // Execute the inline application FSM logic 
            block(addr.toInt(), len.toInt())

            // Release semantics: Tell the NIC hardware we consumed it
            rxConsumer.pointed.value = consSeq + 1u
        }
    }

    /**
     * Replenishes the UMEM hardware ring to allow the NIC to keep writing.
     * Pushes a UMEM frame back to the NIC.
     */
    inline fun pushFill(umemOffset: ULong) {
        val prodSeq = fillProducer.pointed.value
        
        val idx = (prodSeq % FRAME_SIZE.toUInt()).toInt()
        val fillArray = fillDescriptors.reinterpret<ULongVar>()
        
        fillArray[idx] = umemOffset

        // Release semantics: Notify Kernel NIC driver that memory is ready
        fillProducer.pointed.value = prodSeq + 1u
    }

    fun destroy() {
        close(xskObj.pointed.fd)
        free(xskObj)
        free(umemObj)
        free(umemBuffer)
    }
}
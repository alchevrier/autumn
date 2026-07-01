package dev.autumn.annotations

/**
 * Decorates a @BoundaryChannel with an explicit AF_XDP Kernel Bypass configuration.
 * 
 * When the Autumn K2 Compiler encounters this annotation alongside a @BoundaryChannel,
 * it statically binds the channel's memory offsets to the physical hardware ring buffer 
 * mapped to the specified NIC interface and queue.
 * 
 * @param interfaceName The physical or logical NIC interface to bind (e.g. "eth0", "enp3s0", "veth1")
 * @param queueId The explicit hardware DMA interrupt queue. Critical for core-pinning.
 * @param forceCopy When true, emulates XDP via SKB-mode copying. Useful for mock virtual interfaces like veth. Default: false.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class XdpGateway(
    val interfaceName: String,
    val queueId: Int = 0,
    val forceCopy: Boolean = false
)

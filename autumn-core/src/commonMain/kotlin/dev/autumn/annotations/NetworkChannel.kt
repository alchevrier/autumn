package dev.autumn.annotations

/**
 * Maps an AutumnChannel directly to a physical Network Interface Controller (NIC) queue.
 * 
 * The K2 Compiler natively synthesizes target-specific hardware receivers:
 * - Linux Native: Injects AF_XDP/eBPF kernel-bypass drivers pointing UMEM to the Channel's slice.
 * - JVM: Injects NIO DatagramChannels reading directly into the simulated Memory Bank.
 * - macOS: Injects POSIX recvfrom loops.
 * 
 * @param interfaceName The physical or logical NIC interface to bind (e.g. "eth0", "vlan1", "veth1")
 * @param queueId The explicit hardware DMA interrupt queue. Critical for core-pinning.
 * @param port The UDP Multicast or targeted port number to sniff for.
 * @param capacity The size of the Ring Buffer to allocate on the physical chipset.
 * @param sharded The number of parallel HardwareOscillators to spin up for payload mapping logic.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class NetworkChannel(
    val interfaceName: String = "eth0",
    val queueId: Int = 0,
    val port: Int,
    val capacity: Int,
    val sharded: Int = 1
)

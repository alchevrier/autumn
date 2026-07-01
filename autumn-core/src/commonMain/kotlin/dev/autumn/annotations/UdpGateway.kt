package dev.autumn.annotations

/**
 * Decorates a @BoundaryChannel with an explicit POSIX UDP Socket configuration.
 * 
 * When the Autumn K2 Compiler encounters this annotation alongside a @BoundaryChannel,
 * it compiles a non-blocking `recvfrom` polling loop that binds to the specified port
 * natively injecting frames into the BoundaryChannel's memory bank without allocations.
 * 
 * Integrates directly with Phase 5.5 Cloud-Native tiered transports.
 * 
 * @param port The UDP port to bind to.
 * @param host The host address to bind to. Default is "0.0.0.0" (INADDR_ANY)
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class UdpGateway(
    val port: Int,
    val host: String = "0.0.0.0"
)

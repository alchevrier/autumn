package dev.autumn.annotations

/**
 * Decorates a @BoundaryChannel with an explicit Shared Memory IPC configuration.
 * 
 * When the Autumn K2 Compiler encounters this annotation alongside a @BoundaryChannel,
 * it maps the channel's foundational AutumnMemoryBank directly to a shared byte block
 * (typically a POSIX `/dev/shm` temporary file via `mmap` or JVM `MappedByteBuffer`).
 * 
 * This effectively achieves Zero-Copy Multicasting between utterly disjoint processes
 * natively matching ADR-0031 for "HFT Kubernetes".
 * 
 * @param file The shared memory file descriptor path.
 * @param access "WRITE" for the Hot Process generating data; "READ" for Cold Processes tracking data natively.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class IpcGateway(
    val file: String,
    val access: String = "WRITE"
)

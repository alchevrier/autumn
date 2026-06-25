package dev.autumn.annotations

/**
 * Declares a network ingress/egress pathway.
 * 
 * Instead of relying on asynchronous hardware interrupts or naive busy-polling, 
 * this channel is managed by the clock-tick based Cache-Affinity Scheduler. 
 * The scheduler is power-aware: it actively degrades the CPU power mode (e.g., via PAUSE/MWAIT 
 * equivalent instructions) when no data is present to preserve the thermal budget. 
 * When data arrives at a clock tick, the CPU instantly bursts to process it, and the data 
 * is immediately pipelined to the annotated hot-path functions.
 * 
 * - On Linux (HFT config), degrades to Zero-Copy DMA (e.g., AF_XDP) integrated purely via epoll/io_uring.
 * - On standard Linux/JVM, degrades to standard non-blocking epoll, integrated into the scheduler's power-aware tick.
 * - On iOS, degrades to GCD queues.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class NetworkChannel(
    val capacity: Int = 1024,
    val weight: Int = 100,
    val sharded: Int = 1
)

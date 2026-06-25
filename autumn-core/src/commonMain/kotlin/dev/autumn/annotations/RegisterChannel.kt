package dev.autumn.annotations

/**
 * Declares an L1 SPSC (Single Producer Single Consumer) ring buffer channel.
 * Designed for intra-core or tightly coupled pinned core communication.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class RegisterChannel(
    val capacity: Int = 1024,
    val weight: Int = 10,
    val sharded: Int = 1
)

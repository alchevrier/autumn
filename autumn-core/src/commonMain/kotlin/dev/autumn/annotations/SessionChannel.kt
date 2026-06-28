package dev.autumn.annotations

/**
 * Declares a Control Plane (Cold ➔ Hot) injection pathway.
 * 
 * In a Thermal Dynamics architecture, not all data should run on the hot path. 
 * Session channels are used to inject sporadic config, session limits, or risk profiles 
 * into the hot business execution loop without stalling the pipeline.
 *
 * The cold producer pushes data asynchronously, and the Hot Arbiter naturally picks it up
 * during a deterministic clock tick, instantly absorbing the state into the hot memory bank.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class SessionChannel(
    val capacity: Int = 1024,
    val weight: Int = 10,
    val sharded: Int = 1
)
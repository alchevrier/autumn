package dev.autumn.annotations

/**
 * Marks an object or class as exempt from Autumn's zero-allocation hot-path constraints.
 * 
 * Objects annotated with `@LongLived` are permitted to allocate memory exactly once 
 * during startup or IoC container initialization (e.g., event loops, HTTP clients, resolver chains).
 * 
 * The compiler plugin will flag a compilation error if any allocation site reachable 
 * from per-request, per-frame, per-item, or per-keystroke paths is not annotated with 
 * `@LongLived` (or originates from an allowed path).
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class LongLived

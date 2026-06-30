package dev.autumn.annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class InjectTopology(
    val wcetAuditable: Boolean = false
)

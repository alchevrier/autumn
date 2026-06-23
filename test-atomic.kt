import kotlin.concurrent.atomic.AtomicLong

fun main() {
    val a = AtomicLong(0L)
    println(a.get())
}

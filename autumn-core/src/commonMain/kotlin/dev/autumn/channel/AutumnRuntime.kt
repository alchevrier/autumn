package dev.autumn.channel

expect object AutumnRuntime {
    fun spawn(block: () -> Unit)
}

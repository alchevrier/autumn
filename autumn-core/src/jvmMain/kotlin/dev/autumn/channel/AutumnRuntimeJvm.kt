package dev.autumn.channel

actual object AutumnRuntime {
    actual fun spawn(block: () -> Unit) {
        java.lang.Thread(block).start()
    }
}

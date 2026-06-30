package dev.autumn.channel

actual object AutumnRuntime {
    actual fun spawn(block: () -> Unit) {
        // WasmJS is single-threaded, just invoke
        block()
    }

    actual fun spawnOscillator(coreId: Int, tickBlock: () -> Boolean) {
        val scheduler = dev.autumn.scheduler.AutumnScheduler()
        scheduler.powerMode = dev.autumn.scheduler.PowerMode.ACTIVE
        scheduler.bindStaticTopology(tickBlock)
        // Can't run a while(true) blocking loop in JS without freezing the browser, but we satisfy the compiler.
    }
}

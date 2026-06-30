package dev.autumn.channel

actual object AutumnRuntime {
    actual fun spawn(block: () -> Unit) {
        java.lang.Thread(block).start()
    }

    actual fun spawnOscillator(coreId: Int, tickBlock: () -> Boolean) {
        java.lang.Thread {
            val scheduler = dev.autumn.scheduler.AutumnScheduler()
            scheduler.powerMode = dev.autumn.scheduler.PowerMode.ACTIVE
            scheduler.bindStaticTopology(tickBlock)
            scheduler.start(coreId)
        }.apply {
            name = "AutumnOscillator-Core-$coreId"
            start()
        }
    }
}

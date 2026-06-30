package dev.autumn.channel

import kotlin.native.concurrent.Worker
import kotlin.native.concurrent.TransferMode

actual object AutumnRuntime {
        actual fun spawnOscillator(coreId: Int, tickBlock: () -> Boolean) {
        val worker = Worker.start()
        worker.execute(TransferMode.SAFE, { Pair(coreId, tickBlock) }) { (core, tb) ->
            val scheduler = dev.autumn.scheduler.AutumnScheduler()
            scheduler.powerMode = dev.autumn.scheduler.PowerMode.ACTIVE
            scheduler.bindStaticTopology(tb)
            scheduler.start(core)
        }
    }

    actual fun spawn(block: () -> Unit) {
        val worker = Worker.start()
        worker.execute(TransferMode.SAFE, { block }) {
            it()
        }
    }
}

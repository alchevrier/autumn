package dev.autumn.scheduler

import dev.autumn.channel.Channel

/**
 * An execution scheduler that polls a wiring topology of Channels.
 * 
 * Replaces imperative user threads with a unified run-to-completion event loop.
 * An Arbiter receives a subset of channels, unwinds their weights into a flat
 * array schedule, and sweeps them per hardware clock cycle.
 * 
 * Maps directly to ADR-0020: Channel-Driven Dataflow Execution.
 */
class Arbiter(
    val profile: DispatcherProfile = DispatcherProfile.HOT_ISOLATED
) {
    /**
     * A structural binding between a hardware pipe and the component that consumes it.
     */
    private class BoundPipeline(val channel: Channel, val handler: (Int) -> Unit)

    // The declarative wiring topology
    private val registeredChannels = mutableListOf<Pair<BoundPipeline, Int>>()
    
    // The synthesized, branch-predicted execution clock layout
    private var schedule: Array<BoundPipeline> = emptyArray()

    /**
     * Wires a channel into the Arbiter with an explicit priority weight and its downstream pipeline.
     */
    fun addChannel(channel: Channel, weight: Int = 1, handler: (Int) -> Unit) {
        registeredChannels.add(BoundPipeline(channel, handler) to weight)
    }

    /**
     * Synthesizes the flat polling array based on the configured channel weights.
     * Maps the topology into a branch-predicted execution loop.
     */
    fun synthesize() {
        if (registeredChannels.isEmpty()) return
        
        val totalWeight = registeredChannels.sumOf { it.second }
        val newSchedule = arrayOfNulls<BoundPipeline>(totalWeight)
        var index = 0
        
        for ((boundPipeline, weight) in registeredChannels) {
            for (i in 0 until weight) {
                newSchedule[index++] = boundPipeline
            }
        }
        
        @Suppress("UNCHECKED_CAST")
        schedule = newSchedule as Array<BoundPipeline>
    }

    /**
     * Executes a single deterministic sweep across the synthesized channel pipeline.
     * @return true if any element was dequeued and processed.
     */
    fun pollSweep(): Boolean {
        var anyDataProcessed = false

        for (pipeline in schedule) {
            // Unrolled hardware pulse across the circuit
            val idx = pipeline.channel.poll()
            if (idx != -1) {
                anyDataProcessed = true 
                
                // Dispatch exactly to the configured downstream component
                pipeline.handler(idx)
                
                pipeline.channel.commitPoll()
            }
        }

        return anyDataProcessed
    }
}

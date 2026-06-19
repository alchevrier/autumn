package dev.autumn.state

import dev.autumn.annotations.InjectBudget
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A Hardware-Sympathetic Zero-Allocation Reactivity Engine.
 * 
 * Instead of instantiating an Observer or Flow for every UI element,
 * we emulate an FPGA Interrupt Wire and Version Registers.
 */
class EpochStateEngine(
    @InjectBudget("state_epoch_matrix")
    private val budget: Int
) {
    // 1. The Version Registers
    private val epochs = IntArray(budget)
    
    // 2. The Interrupt Wire
    // A single, global, coalesced signal.
    // Using MutableStateFlow guarantees dropping completely replacing intermediate state mutations
    private val globalInterruptWire = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    fun incrementEpoch(slotIndex: Int) {
        if (slotIndex in epochs.indices) {
            epochs[slotIndex]++
            globalInterruptWire.tryEmit(Unit)
        }
    }

    fun readEpoch(slotIndex: Int): Int {
        return if (slotIndex in epochs.indices) epochs[slotIndex] else -1
    }

    fun acquireInterruptWire(): Flow<Unit> {
        return globalInterruptWire.map { Unit }
    }
}

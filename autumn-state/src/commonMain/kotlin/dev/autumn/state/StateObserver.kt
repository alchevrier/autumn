package dev.autumn.state

/**
 * Interface for observing state transitions without allocating lambda closures.
 */
interface StateObserver {
    fun onStateChanged(previousStateId: Int, newStateId: Int, documentId: Int)
}

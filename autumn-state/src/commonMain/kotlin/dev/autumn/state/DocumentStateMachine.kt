package dev.autumn.state

import dev.autumn.annotations.LongLived

/**
 * A strictly zero-allocation Finite State Machine (FSM).
 * 
 * Instead of polymorphic objects or nested lambdas, this machine implements 
 * standard FSM principles using flat, cache-aware 1D primitive array matrices.
 * This directly aligns with the 'Circuit-Based Programming' methodology: 
 * states and transitions are predetermined static matrices, replacing complex
 * UI navigation logic with fixed, verifiable state transitions.
 *
 * @param maxStates The maximum number of states.
 * @param maxEvents The maximum number of events available.
 */
@LongLived
class DocumentStateMachine(
    private val maxStates: Int,
    private val maxEvents: Int
) {
    // 2D Matrix flattened into 1D (fromState X event -> toState)
    // Size = maxStates * maxEvents. Defaults to -1 (Illegal Transition).
    private val transitions = IntArray(maxStates * maxEvents) { -1 }
    
    // Maps each state directly to a logical Document Bucket ID to render
    private val documents = IntArray(maxStates) { -1 }
    
    // Up to 8 observers strictly sized to avert Iterator/ArrayList mutations
    private val observers = arrayOfNulls<StateObserver>(8)
    private var observerCount = 0

    var currentStateId: Int = 0
        private set

    val currentDocumentId: Int
        get() = documents[currentStateId]

    fun defineState(stateId: Int, documentId: Int) {
        documents[stateId] = documentId
    }

    fun defineTransition(fromStateId: Int, eventId: Int, toStateId: Int) {
        val index = (fromStateId * maxEvents) + eventId
        transitions[index] = toStateId
    }

    fun addObserver(observer: StateObserver) {
        if (observerCount >= observers.size) {
            throw IllegalStateException("Max observer capacity reached")
        }
        observers[observerCount++] = observer
    }

    /**
     * Dispatch an event to advance the state machine.
     * @return true if the event successfully triggered a transition, false otherwise.
     */
    fun dispatch(eventId: Int): Boolean {
        val index = (currentStateId * maxEvents) + eventId
        val nextStateId = transitions[index]
        
        if (nextStateId != -1) {
            val prevStateId = currentStateId
            currentStateId = nextStateId
            notifyObservers(prevStateId, nextStateId, documents[nextStateId])
            return true
        }
        
        return false // Ignore unhandled events strictly
    }

    fun forceState(stateId: Int) {
        val prevStateId = currentStateId
        currentStateId = stateId
        notifyObservers(prevStateId, stateId, documents[stateId])
    }

    private fun notifyObservers(prevStateId: Int, nextStateId: Int, documentId: Int) {
        // Flat indexed loop avoids Iterator object allocation!
        for (i in 0 until observerCount) {
            observers[i]?.onStateChanged(prevStateId, nextStateId, documentId)
        }
    }
}

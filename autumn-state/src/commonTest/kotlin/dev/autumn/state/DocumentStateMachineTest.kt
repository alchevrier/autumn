package dev.autumn.state

import dev.autumn.annotations.LongLived
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@LongLived
class DocumentStateMachineTest {

    // Mock Events
    private val EVENT_NEXT = 0
    private val EVENT_BACK = 1
    private val EVENT_ERROR = 2

    // Mock States
    private val STATE_INIT = 0
    private val STATE_LOADING = 1
    private val STATE_SUCCESS = 2
    private val STATE_ERROR_SCREEN = 3

    // Mock Bucket Document IDs
    private val DOC_HOME = 100
    private val DOC_SPINNER = 101
    private val DOC_DASHBOARD = 102
    private val DOC_RETRY = 103

    @Test
    fun `test valid and invalid transitions`() {
        // App has 4 states and 3 common events
        val fsm = DocumentStateMachine(maxStates = 4, maxEvents = 3)

        fsm.defineState(STATE_INIT, DOC_HOME)
        fsm.defineState(STATE_LOADING, DOC_SPINNER)
        fsm.defineState(STATE_SUCCESS, DOC_DASHBOARD)
        fsm.defineState(STATE_ERROR_SCREEN, DOC_RETRY)

        // INIT ->(NEXT)-> LOADING
        fsm.defineTransition(STATE_INIT, EVENT_NEXT, STATE_LOADING)
        
        // LOADING ->(NEXT)-> SUCCESS
        fsm.defineTransition(STATE_LOADING, EVENT_NEXT, STATE_SUCCESS)
        
        // LOADING ->(ERROR)-> ERROR_SCREEN
        fsm.defineTransition(STATE_LOADING, EVENT_ERROR, STATE_ERROR_SCREEN)

        // ERROR_SCREEN ->(RETRY/BACK)-> INIT
        fsm.defineTransition(STATE_ERROR_SCREEN, EVENT_BACK, STATE_INIT)

        fsm.forceState(STATE_INIT)

        // Invalid Transition
        assertFalse(fsm.dispatch(EVENT_BACK))
        assertEquals(STATE_INIT, fsm.currentStateId)

        // Valid Transitions
        assertTrue(fsm.dispatch(EVENT_NEXT))
        assertEquals(STATE_LOADING, fsm.currentStateId)
        assertEquals(DOC_SPINNER, fsm.currentDocumentId)

        assertTrue(fsm.dispatch(EVENT_ERROR))
        assertEquals(STATE_ERROR_SCREEN, fsm.currentStateId)
        assertEquals(DOC_RETRY, fsm.currentDocumentId)

        assertTrue(fsm.dispatch(EVENT_BACK))
        assertEquals(STATE_INIT, fsm.currentStateId)
    }

    @Test
    fun `test state observer zero-allocation notification`() {
        val fsm = DocumentStateMachine(maxStates = 2, maxEvents = 1)
        fsm.defineState(0, 500)
        fsm.defineState(1, 501)
        fsm.defineTransition(0, 0, 1)

        var notifiedCount = 0
        var lastDocId = -1

        val observer = object : StateObserver {
            override fun onStateChanged(previousStateId: Int, newStateId: Int, documentId: Int) {
                notifiedCount++
                lastDocId = documentId
            }
        }

        fsm.addObserver(observer)
        fsm.forceState(0)
        assertEquals(1, notifiedCount)
        assertEquals(500, lastDocId)

        fsm.dispatch(0)
        assertEquals(2, notifiedCount)
        assertEquals(501, lastDocId)
    }
}

package com.example.voiceassistant

import com.example.voiceassistant.domain.model.AssistantState
import com.example.voiceassistant.domain.model.SensitiveAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantStateTest {

    @Test
    fun testIdleState() {
        val state: AssistantState = AssistantState.Idle
        assertTrue(state is AssistantState.Idle)
    }

    @Test
    fun testListeningStateMaintainsPartialSpeech() {
        val listening = AssistantState.Listening(partialText = "Set a timer", rmsDb = 5.2f)
        assertEquals("Set a timer", listening.partialText)
        assertEquals(5.2f, listening.rmsDb, 0.01f)
    }

    @Test
    fun testWaitingForConfirmationHoldsAction() {
        val action = SensitiveAction.SendMessage("Alice", "Hello")
        val waiting = AssistantState.WaitingForConfirmation(
            pendingAction = action,
            prompt = "Confirm sending message to Alice"
        )

        assertEquals("Alice", (waiting.pendingAction as SensitiveAction.SendMessage).recipient)
        assertEquals("Hello", (waiting.pendingAction as SensitiveAction.SendMessage).messageText)
    }

    @Test
    fun testErrorStateStoresErrorMessage() {
        val error = AssistantState.Error("Microphone permission denied")
        assertEquals("Microphone permission denied", error.errorMessage)
        assertTrue(error.canRetry)
    }
}

package com.example.voiceassistant.domain.model

/**
 * State representing the active lifecycle of the voice assistant.
 */
sealed class AssistantState {
    object Idle : AssistantState()

    data class Listening(
        val partialText: String = "",
        val rmsDb: Float = 0f
    ) : AssistantState()

    data class Processing(
        val recognizedText: String
    ) : AssistantState()

    data class Speaking(
        val speechText: String
    ) : AssistantState()

    data class WaitingForConfirmation(
        val pendingAction: SensitiveAction,
        val prompt: String
    ) : AssistantState()

    data class Error(
        val errorMessage: String,
        val canRetry: Boolean = true
    ) : AssistantState()
}

/**
 * Chat history message item.
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionBadge: String? = null
)

enum class MessageSender {
    USER,
    ASSISTANT,
    SYSTEM
}

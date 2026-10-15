package com.example.voiceassistant.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voiceassistant.data.ActionExecutor
import com.example.voiceassistant.data.AiChatClient
import com.example.voiceassistant.data.SpeechRecognizerManager
import com.example.voiceassistant.data.TextToSpeechManager
import com.example.voiceassistant.domain.model.AssistantAction
import com.example.voiceassistant.domain.model.AssistantState
import com.example.voiceassistant.domain.model.ChatMessage
import com.example.voiceassistant.domain.model.ExecutionResult
import com.example.voiceassistant.domain.model.MessageSender
import com.example.voiceassistant.domain.parser.CommandParser
import com.example.voiceassistant.domain.parser.ConfirmationDecision
import com.example.voiceassistant.service.VoiceAssistantForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val commandParser = CommandParser()
    private val actionExecutor = ActionExecutor(application.applicationContext)
    private val aiChatClient = AiChatClient(application.applicationContext)

    private var speechRecognizerManager: SpeechRecognizerManager? = null
    private var textToSpeechManager: TextToSpeechManager? = null

    private val _assistantState = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = "Hello! I am your Voice Assistant. Tap 'Start Listening' or type a command to get started."
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private val _plannedAction = MutableStateFlow<AssistantAction?>(null)
    val plannedAction: StateFlow<AssistantAction?> = _plannedAction.asStateFlow()

    private val _showPrivacyDialog = MutableStateFlow(false)
    val showPrivacyDialog: StateFlow<Boolean> = _showPrivacyDialog.asStateFlow()

    private val _textInput = MutableStateFlow("")
    val textInput: StateFlow<String> = _textInput.asStateFlow()

    val isForegroundServiceRunning: StateFlow<Boolean> =
        VoiceAssistantForegroundService.isServiceRunning

    init {
        initSpeechEngines()
    }

    private fun initSpeechEngines() {
        textToSpeechManager = TextToSpeechManager(getApplication())

        speechRecognizerManager = SpeechRecognizerManager(
            context = getApplication(),
            onReady = {
                _assistantState.value = AssistantState.Listening("", 0f)
            },
            onRmsChanged = { rmsDb ->
                val current = _assistantState.value
                if (current is AssistantState.Listening) {
                    _assistantState.value = current.copy(rmsDb = rmsDb)
                }
            },
            onPartialResult = { partial ->
                val current = _assistantState.value
                if (current is AssistantState.Listening) {
                    _assistantState.value = current.copy(partialText = partial)
                }
            },
            onFinalResult = { finalResult ->
                handleUserInput(finalResult)
            },
            onError = { errorMsg ->
                _assistantState.value = AssistantState.Error(errorMsg)
                speakResponse("Sorry, $errorMsg")
            }
        )
    }

    fun startListening() {
        textToSpeechManager?.stop()
        _assistantState.value = AssistantState.Listening()
        speechRecognizerManager?.startListening()
    }

    fun stopListening() {
        speechRecognizerManager?.stopListening()
    }

    fun cancelAll() {
        speechRecognizerManager?.cancel()
        textToSpeechManager?.stop()
        _plannedAction.value = null
        _assistantState.value = AssistantState.Idle
    }

    fun onTextInputChange(newText: String) {
        _textInput.value = newText
    }

    fun submitTextInput() {
        val query = _textInput.value.trim()
        if (query.isNotBlank()) {
            _textInput.value = ""
            handleUserInput(query)
        }
    }

    private fun handleUserInput(input: String) {
        val currentState = _assistantState.value

        // Check if we are currently awaiting confirmation
        if (currentState is AssistantState.WaitingForConfirmation) {
            handleConfirmationVoiceInput(input)
            return
        }

        _lastRecognizedText.value = input
        _assistantState.value = AssistantState.Processing(input)
        addMessage(MessageSender.USER, input)

        val action = commandParser.parseCommand(input)
        _plannedAction.value = action

        when (action) {
            is AssistantAction.RequireConfirmation -> {
                _assistantState.value = AssistantState.WaitingForConfirmation(
                    pendingAction = action.action,
                    prompt = action.explanation
                )
                val voicePrompt = "${action.explanation}. Say 'confirm' to proceed or 'cancel' to abort."
                speakResponse(voicePrompt, preserveCurrentState = true)
            }

            is AssistantAction.Unknown -> {
                if (aiChatClient.isConfigured) {
                    viewModelScope.launch {
                        _assistantState.value = AssistantState.Processing("Thinking…")
                        try {
                            val reply = aiChatClient.reply(_messages.value)
                            _assistantState.value = AssistantState.Idle
                            addMessage(MessageSender.ASSISTANT, reply, actionBadge = "AI")
                            speakResponse(reply)
                        } catch (error: Exception) {
                            val responseText = "I couldn't reach the AI service. Check your connection and AI setup. ${error.message.orEmpty()}"
                            _assistantState.value = AssistantState.Idle
                            addMessage(MessageSender.ASSISTANT, responseText, actionBadge = "AI unavailable")
                            speakResponse(responseText)
                        }
                    }
                } else {
                    val responseText = "AI chat isn't configured yet. ${action.reason} Add SUPABASE_URL and SUPABASE_ANON_KEY to VA/local.properties, deploy the assistant-chat function, and set OPENAI_API_KEY as a Supabase secret."
                    _assistantState.value = AssistantState.Idle
                    addMessage(MessageSender.ASSISTANT, responseText, actionBadge = "Setup required")
                    speakResponse(responseText)
                }
            }

            else -> {
                executeParsedAction(action)
            }
        }
    }

    private fun handleConfirmationVoiceInput(input: String) {
        when (commandParser.parseConfirmationResponse(input)) {
            ConfirmationDecision.CONFIRMED -> {
                confirmPendingAction()
            }
            ConfirmationDecision.CANCELLED -> {
                rejectPendingAction()
            }
            ConfirmationDecision.UNCLEAR -> {
                val prompt = "I did not catch your confirmation. Please say 'confirm' or 'cancel', or use the screen buttons."
                speakResponse(prompt)
            }
        }
    }

    /**
     * Executes only the action currently displayed for confirmation.
     * Moving out of WaitingForConfirmation before executing makes confirmation one-shot.
     */
    fun confirmPendingAction() {
        val waiting = _assistantState.value as? AssistantState.WaitingForConfirmation ?: return
        val pendingAction = waiting.pendingAction
        _assistantState.value = AssistantState.Processing("Executing confirmed action")
        _plannedAction.value = null
        val result = actionExecutor.executeSensitiveAction(pendingAction)
        handleExecutionResult(result, actionBadge = "Action Confirmed")
    }

    fun rejectPendingAction() {
        if (_assistantState.value !is AssistantState.WaitingForConfirmation) return
        _plannedAction.value = null
        _assistantState.value = AssistantState.Idle
        val message = "Action cancelled."
        addMessage(MessageSender.ASSISTANT, message, actionBadge = "Cancelled")
        speakResponse(message)
    }

    private fun executeParsedAction(action: AssistantAction) {
        _assistantState.value = AssistantState.Processing("Executing action")
        val result = actionExecutor.execute(action)
        val badge = when (action) {
            is AssistantAction.TellTime -> "Time"
            is AssistantAction.TellDate -> "Date"
            is AssistantAction.SetTimer -> "Timer"
            is AssistantAction.SetAlarm -> "Alarm"
            is AssistantAction.OpenApp -> "App Launch"
            is AssistantAction.OpenSetting -> "Settings"
            else -> null
        }
        handleExecutionResult(result, badge)
    }

    private fun handleExecutionResult(result: ExecutionResult, actionBadge: String?) {
        when (result) {
            is ExecutionResult.Success -> {
                _assistantState.value = AssistantState.Idle
                addMessage(MessageSender.ASSISTANT, result.message, actionBadge = actionBadge)
                speakResponse(result.message)
            }
            is ExecutionResult.Cancelled -> {
                _assistantState.value = AssistantState.Idle
                addMessage(MessageSender.ASSISTANT, result.message, actionBadge = "Cancelled")
                speakResponse(result.message)
            }
            is ExecutionResult.Failure -> {
                _assistantState.value = AssistantState.Error(result.error)
                addMessage(MessageSender.ASSISTANT, "Error: ${result.error}", actionBadge = "Error")
                speakResponse("Error: ${result.error}")
            }
        }
    }

    private fun speakResponse(text: String, preserveCurrentState: Boolean = false) {
        if (!preserveCurrentState) _assistantState.value = AssistantState.Speaking(text)
        textToSpeechManager?.speak(
            text = text,
            onStart = { },
            onDone = {
                // Never restore stale state after a user has confirmed or cancelled an action.
                if (!preserveCurrentState && _assistantState.value is AssistantState.Speaking) {
                    _assistantState.value = AssistantState.Idle
                }
            },
            onError = {
                if (!preserveCurrentState && _assistantState.value is AssistantState.Speaking) {
                    _assistantState.value = AssistantState.Idle
                }
            }
        )
    }

    private fun addMessage(sender: MessageSender, text: String, actionBadge: String? = null) {
        val updated = _messages.value.toMutableList().apply {
            add(ChatMessage(sender = sender, text = text, actionBadge = actionBadge))
        }
        _messages.value = updated
    }

    fun toggleForegroundService(context: Context, enable: Boolean) {
        val serviceIntent = Intent(context, VoiceAssistantForegroundService::class.java).apply {
            action = if (enable) {
                VoiceAssistantForegroundService.ACTION_START_SERVICE
            } else {
                VoiceAssistantForegroundService.ACTION_STOP_SERVICE
            }
        }

        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            context.stopService(serviceIntent)
        }
    }

    fun openPrivacyDialog() {
        _showPrivacyDialog.value = true
    }

    fun closePrivacyDialog() {
        _showPrivacyDialog.value = false
    }

    override fun onCleared() {
        speechRecognizerManager?.destroy()
        textToSpeechManager?.shutdown()
        super.onCleared()
    }
}

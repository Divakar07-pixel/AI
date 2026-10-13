package com.example.voiceassistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voiceassistant.domain.model.AssistantState
import com.example.voiceassistant.ui.components.ActionCard
import com.example.voiceassistant.ui.components.BackgroundWakeWordCard
import com.example.voiceassistant.ui.components.ChatTranscript
import com.example.voiceassistant.ui.components.ConfirmationDialog
import com.example.voiceassistant.ui.components.MicControls
import com.example.voiceassistant.ui.components.PrivacyDisclaimerDialog
import com.example.voiceassistant.ui.components.StatusHeader
import com.example.voiceassistant.ui.theme.AssistantPrimary
import com.example.voiceassistant.ui.theme.ErrorRed

@Composable
fun VoiceAssistantScreen(
    viewModel: MainViewModel,
    hasRecordAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.assistantState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val lastRecognized by viewModel.lastRecognizedText.collectAsState()
    val plannedAction by viewModel.plannedAction.collectAsState()
    val isServiceRunning by viewModel.isForegroundServiceRunning.collectAsState()
    val showPrivacyDialog by viewModel.showPrivacyDialog.collectAsState()
    val textInput by viewModel.textInput.collectAsState()

    Scaffold(
        topBar = {
            StatusHeader(
                state = state,
                onInfoClick = { viewModel.openPrivacyDialog() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission Denial Banner
            AnimatedVisibility(visible = !hasRecordAudioPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ErrorRed.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MicOff,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Microphone access is required for speech recognition.",
                                color = ErrorRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        OutlinedButton(onClick = onRequestPermission) {
                            Text(text = "Grant", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Top action preview and background wake-word toggle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                ActionCard(
                    lastRecognized = lastRecognized,
                    plannedAction = plannedAction
                )

                Spacer(modifier = Modifier.height(6.dp))

                BackgroundWakeWordCard(
                    isServiceRunning = isServiceRunning,
                    onToggleService = { enable ->
                        if (enable && !hasRecordAudioPermission) {
                            onRequestPermission()
                        } else {
                            viewModel.toggleForegroundService(context, enable)
                        }
                    }
                )

                // Confirmation card when waiting for confirmation
                val waitingState = state as? AssistantState.WaitingForConfirmation
                if (waitingState != null) {
                    ConfirmationDialog(
                        pendingAction = waitingState.pendingAction,
                        prompt = waitingState.prompt,
                        onConfirm = { viewModel.confirmPendingAction() },
                        onCancel = { viewModel.rejectPendingAction() }
                    )
                }
            }

            // Chat Transcript List (Conversation History)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                ChatTranscript(messages = messages)
            }

            // Bottom Section: Quick suggestions + Text Input + Mic Push-to-talk
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(bottom = 16.dp, top = 8.dp)
            ) {
                // Quick suggestion chips
                val suggestions = listOf(
                    "What time is it?",
                    "Today's date",
                    "Set a timer for 5 minutes",
                    "Open Camera",
                    "Open Wi-Fi settings",
                    "Call Mom"
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestions) { suggestion ->
                        AssistChip(
                            onClick = {
                                viewModel.onTextInputChange(suggestion)
                                viewModel.submitTextInput()
                            },
                            label = { Text(text = suggestion, fontSize = 12.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Text Input fallback for typing commands
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { viewModel.onTextInputChange(it) },
                        placeholder = { Text("Or type a command...", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.submitTextInput() },
                        enabled = textInput.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send Command",
                            tint = if (textInput.isNotBlank()) AssistantPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Start / Stop Listening Push-to-talk Controls
                MicControls(
                    state = state,
                    onStartListening = {
                        if (!hasRecordAudioPermission) {
                            onRequestPermission()
                        } else {
                            viewModel.startListening()
                        }
                    },
                    onStopListening = { viewModel.stopListening() },
                    onCancelAll = { viewModel.cancelAll() }
                )
            }
        }
    }

    if (showPrivacyDialog) {
        PrivacyDisclaimerDialog(
            onDismiss = { viewModel.closePrivacyDialog() }
        )
    }
}

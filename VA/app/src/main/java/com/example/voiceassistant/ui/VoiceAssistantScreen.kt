package com.example.voiceassistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voiceassistant.domain.model.AssistantState
import com.example.voiceassistant.ui.components.ActionCard
import com.example.voiceassistant.ui.components.ChatTranscript
import com.example.voiceassistant.ui.components.ConfirmationDialog
import com.example.voiceassistant.ui.components.MicControls
import com.example.voiceassistant.ui.components.PrivacyDisclaimerDialog

private val Night = Color(0xFF070B16)
private val Panel = Color(0xFF10182A)
private val Cyan = Color(0xFF5DE4FF)
private val Violet = Color(0xFF8875FF)
private val Muted = Color(0xFF9AA9C4)

@Composable
fun VoiceAssistantScreen(
    viewModel: MainViewModel,
    hasRecordAudioPermission: Boolean,
    hasContactsPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRequestContactsPermission: () -> Unit,
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
        containerColor = Night,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0C1426), Night, Color(0xFF090D19))
                    )
                )
                .padding(innerPadding)
        ) {
            // Branded header and current assistant state.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            Brush.linearGradient(listOf(Cyan.copy(alpha = 0.22f), Violet.copy(alpha = 0.28f))),
                            CircleShape
                        )
                        .border(1.dp, Cyan.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Cyan, modifier = Modifier.size(25.dp))
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("J.A.R.V.I.S.", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("PERSONAL AI ASSISTANT", color = Muted, fontSize = 9.sp, letterSpacing = 1.6.sp)
                }
                IconButton(onClick = { viewModel.openPrivacyDialog() }) {
                    Icon(Icons.Default.Info, contentDescription = "Privacy and app information", tint = Muted)
                }
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(stateColor(state), CircleShape)
                )
                Text(
                    text = stateLabel(state),
                    color = stateColor(state),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 7.dp)
                )
                Spacer(Modifier.weight(1f))
                Text("EN • தமிழ் • TANGLISH", color = Muted, fontSize = 9.sp, letterSpacing = 0.5.sp)
            }

            AnimatedVisibility(visible = !hasRecordAudioPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1D2A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MicOff, contentDescription = null, tint = Color(0xFFFF8796))
                        Text(
                            "Microphone permission is needed for voice commands.",
                            color = Color(0xFFFFD7DE),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        OutlinedButton(onClick = onRequestPermission) { Text("Grant") }
                    }
                }
            }

            AnimatedVisibility(visible = !hasContactsPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17243A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Cyan)
                        Text(
                            "Contacts permission lets me open the exact WhatsApp chat by name.",
                            color = Color(0xFFD8E7FF),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        OutlinedButton(onClick = onRequestContactsPermission) { Text("Grant") }
                    }
                }
            }

            // Animated listening core. It is a visual state indicator, not a wake-word detector.
            VoiceOrb(state = state)

            Text(
                text = when (state) {
                    is AssistantState.Listening -> "I'M LISTENING"
                    is AssistantState.Processing -> "THINKING"
                    is AssistantState.Speaking -> "RESPONSE READY"
                    is AssistantState.WaitingForConfirmation -> "CONFIRMATION REQUIRED"
                    is AssistantState.Error -> "NEEDS ATTENTION"
                    else -> "HOW CAN I HELP YOU?"
                },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp)
            )
            Text(
                text = when (state) {
                    is AssistantState.Listening -> (state as AssistantState.Listening).partialText.ifBlank { "Speak naturally in English or Tanglish" }
                    is AssistantState.Processing -> "Working on your request…"
                    is AssistantState.WaitingForConfirmation -> "Please review the action below"
                    is AssistantState.Error -> (state as AssistantState.Error).errorMessage
                    else -> "Try “time enna?” or “Camera open pannu”"
                },
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 20.dp, vertical = 5.dp)
            )

            AnimatedVisibility(visible = plannedAction != null) {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 4.dp)) {
                    ActionCard(lastRecognized = lastRecognized, plannedAction = plannedAction)
                }
            }

            val waitingState = state as? AssistantState.WaitingForConfirmation
            AnimatedVisibility(visible = waitingState != null) {
                if (waitingState != null) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        ConfirmationDialog(
                            pendingAction = waitingState.pendingAction,
                            prompt = waitingState.prompt,
                            onConfirm = { viewModel.confirmPendingAction() },
                            onCancel = { viewModel.rejectPendingAction() }
                        )
                    }
                }
            }

            // Conversation is the flexible centre of the screen.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .background(Panel.copy(alpha = 0.82f), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 4.dp)
            ) {
                if (messages.isEmpty()) {
                    Text("Your conversation will appear here.", color = Muted, modifier = Modifier.align(Alignment.Center))
                } else {
                    ChatTranscript(messages = messages)
                }
            }

            // Quick actions include native commands and Tanglish examples.
            val suggestions = listOf(
                "What time is it?",
                "Today's date",
                "Set a timer for 5 minutes",
                "Open Camera",
                "wifi settings open pannu",
                "time enna?"
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestions) { suggestion ->
                    AssistChip(
                        onClick = {
                            viewModel.onTextInputChange(suggestion)
                            viewModel.submitTextInput()
                        },
                        label = { Text(suggestion, fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF17243A),
                            labelColor = Color(0xFFD8E7FF)
                        ),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { viewModel.onTextInputChange(it) },
                    placeholder = { Text("Ask or type a command…", fontSize = 13.sp, color = Muted) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = Panel,
                        unfocusedContainerColor = Panel,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Cyan,
                        unfocusedIndicatorColor = Color(0xFF2B3B57),
                        cursorColor = Cyan
                    )
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = { viewModel.submitTextInput() },
                    enabled = textInput.isNotBlank(),
                    shape = CircleShape,
                    color = if (textInput.isNotBlank()) Cyan else Color(0xFF253047),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Send, contentDescription = "Send message", tint = Night, modifier = Modifier.size(20.dp))
                    }
                }
            }

            MicControls(
                state = state,
                onStartListening = {
                    if (!hasRecordAudioPermission) onRequestPermission() else viewModel.startListening()
                },
                onStopListening = { viewModel.stopListening() },
                onCancelAll = { viewModel.cancelAll() },
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )

            // Compact, explicit control for the experimental foreground-service demo.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Background service (experimental)", color = Color(0xFFD8E7FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("Wake-word detection is not implemented", color = Muted, fontSize = 9.sp)
                }
                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { enable ->
                        if (enable && !hasRecordAudioPermission) onRequestPermission()
                        else viewModel.toggleForegroundService(context, enable)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Cyan)
                )
            }
        }
    }

    if (showPrivacyDialog) {
        PrivacyDisclaimerDialog(onDismiss = { viewModel.closePrivacyDialog() })
    }
}

@Composable
private fun VoiceOrb(state: AssistantState) {
    val active = state is AssistantState.Listening || state is AssistantState.Processing || state is AssistantState.Speaking
    val transition = rememberInfiniteTransition(label = "jarvis-orb")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = if (active) 1.10f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb-pulse"
    )

    Box(
        modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .scale(pulse)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Cyan.copy(alpha = 0.18f), Violet.copy(alpha = 0.10f), Color.Transparent)
                    ),
                    CircleShape
                )
                .border(1.dp, Cyan.copy(alpha = if (active) 0.45f else 0.20f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF243D65), Color(0xFF14223D), Color(0xFF0B1222))
                    ),
                    CircleShape
                )
                .border(1.dp, Violet.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = "Assistant voice activity",
                tint = Cyan,
                modifier = Modifier.size(48.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 1.dp)
                .size(6.dp)
                .background(stateColor(state), CircleShape)
        )
    }
}

private fun stateLabel(state: AssistantState): String = when (state) {
    is AssistantState.Idle -> "READY • PUSH TO TALK"
    is AssistantState.Listening -> "LISTENING"
    is AssistantState.Processing -> "PROCESSING REQUEST"
    is AssistantState.Speaking -> "SPEAKING"
    is AssistantState.WaitingForConfirmation -> "WAITING FOR CONFIRMATION"
    is AssistantState.Error -> "ASSISTANT ERROR"
}

private fun stateColor(state: AssistantState): Color = when (state) {
    is AssistantState.Idle -> Color(0xFF56D6A0)
    is AssistantState.Listening -> Cyan
    is AssistantState.Processing -> Violet
    is AssistantState.Speaking -> Cyan
    is AssistantState.WaitingForConfirmation -> Color(0xFFFFC857)
    is AssistantState.Error -> Color(0xFFFF7785)
}

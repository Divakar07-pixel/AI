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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voiceassistant.domain.model.AssistantAction
import com.example.voiceassistant.domain.model.AssistantState
import com.example.voiceassistant.domain.model.ChatMessage
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
private val Border = Color.White.copy(alpha = 0.08f)

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
    val waitingState = state as? AssistantState.WaitingForConfirmation

    Scaffold(containerColor = Night, modifier = modifier.fillMaxSize()) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0C1426), Night, Color(0xFF090D19))))
                .padding(innerPadding)
        ) {
            // Width-based breakpoints adapt to phones, landscape, split-screen, tablets and resizable windows.
            val expanded = maxWidth >= 840.dp
            val medium = maxWidth >= 600.dp

            Column(Modifier.fillMaxSize()) {
                Header(onPrivacyClick = { viewModel.openPrivacyDialog() }, modifier = Modifier.fillMaxWidth().padding(horizontal = if (expanded) 28.dp else 18.dp))
                StatusLine(state = state, medium = medium)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = if (expanded) 24.dp else 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!hasRecordAudioPermission) {
                        PermissionBanner(
                            title = "Microphone permission required",
                            buttonText = "Grant",
                            accent = Color(0xFFFF8796),
                            modifier = Modifier.weight(1f),
                            onClick = onRequestPermission
                        )
                    }
                    if (!hasContactsPermission) {
                        PermissionBanner(
                            title = "Contacts enables name-based chat lookup",
                            buttonText = "Grant",
                            accent = Cyan,
                            modifier = Modifier.weight(1f),
                            onClick = onRequestContactsPermission
                        )
                    }
                }

                if (expanded) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.42f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .background(Panel.copy(alpha = 0.72f), RoundedCornerShape(24.dp))
                                .border(1.dp, Border, RoundedCornerShape(24.dp))
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VoiceOrb(state = state, diameter = 124.dp)
                            StateSummary(state = state, centered = false)
                            PendingActionContent(lastRecognized, plannedAction, waitingState, viewModel)
                            QuickActions(viewModel)
                            CommandInput(textInput, viewModel::onTextInputChange, viewModel::submitTextInput)
                            MicControls(
                                state = state,
                                onStartListening = {
                                    if (!hasRecordAudioPermission) onRequestPermission() else viewModel.startListening()
                                },
                                onStopListening = { viewModel.stopListening() },
                                onCancelAll = { viewModel.cancelAll() },
                                modifier = Modifier.fillMaxWidth()
                            )
                            WakeWordControl(
                                isServiceRunning = isServiceRunning,
                                hasRecordAudioPermission = hasRecordAudioPermission,
                                onRequestPermission = onRequestPermission,
                                onToggle = { enabled -> viewModel.toggleForegroundService(context, enabled) }
                            )
                        }

                        ConversationPanel(messages, Modifier.weight(0.58f).fillMaxHeight())
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (medium) 16.dp else 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        VoiceOrb(state = state, diameter = if (medium) 112.dp else 94.dp)
                        StateSummary(state = state, centered = true)
                        PendingActionContent(lastRecognized, plannedAction, waitingState, viewModel)
                        ConversationPanel(messages, Modifier.weight(1f).fillMaxWidth())
                        QuickActions(viewModel)
                        CommandInput(textInput, viewModel::onTextInputChange, viewModel::submitTextInput)
                        MicControls(
                            state = state,
                            onStartListening = {
                                if (!hasRecordAudioPermission) onRequestPermission() else viewModel.startListening()
                            },
                            onStopListening = { viewModel.stopListening() },
                            onCancelAll = { viewModel.cancelAll() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        WakeWordControl(
                            isServiceRunning = isServiceRunning,
                            hasRecordAudioPermission = hasRecordAudioPermission,
                            onRequestPermission = onRequestPermission,
                            onToggle = { enabled -> viewModel.toggleForegroundService(context, enabled) }
                        )
                    }
                }
            }
        }
    }

    if (showPrivacyDialog) {
        PrivacyDisclaimerDialog(onDismiss = { viewModel.closePrivacyDialog() })
    }
}

@Composable
private fun Header(onPrivacyClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Brush.linearGradient(listOf(Cyan.copy(alpha = 0.22f), Violet.copy(alpha = 0.28f))), CircleShape)
                .border(1.dp, Cyan.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Cyan, modifier = Modifier.size(25.dp))
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text("J.A.R.V.I.S.", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("PERSONAL AI ASSISTANT", color = Muted, fontSize = 9.sp, letterSpacing = 1.6.sp)
        }
        IconButton(onClick = onPrivacyClick) { Icon(Icons.Default.Info, contentDescription = "Privacy and app information", tint = Muted) }
    }
}

@Composable
private fun StatusLine(state: AssistantState, medium: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (medium) 28.dp else 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(stateColor(state), CircleShape))
        Text(stateLabel(state), color = stateColor(state), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 7.dp))
        Spacer(Modifier.weight(1f))
        Text("EN • தமிழ் • TANGLISH", color = Muted, fontSize = 9.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun PermissionBanner(
    title: String,
    buttonText: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.MicOff, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Text(title, color = Color(0xFFE5EDFF), fontSize = 11.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                Text(buttonText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StateSummary(state: AssistantState, centered: Boolean) {
    val title = when (state) {
        is AssistantState.Listening -> "I'M LISTENING"
        is AssistantState.Processing -> "THINKING"
        is AssistantState.Speaking -> "RESPONSE READY"
        is AssistantState.WaitingForConfirmation -> "CONFIRMATION REQUIRED"
        is AssistantState.Error -> "NEEDS ATTENTION"
        else -> "HOW CAN I HELP YOU?"
    }
    val subtitle = when (state) {
        is AssistantState.Listening -> state.partialText.ifBlank { "Speak naturally in English or Tanglish" }
        is AssistantState.Processing -> "Working on your request…"
        is AssistantState.WaitingForConfirmation -> "Review the action before continuing"
        is AssistantState.Error -> state.errorMessage
        else -> "Try “time enna?” or “Camera open pannu”"
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
        Text(subtitle, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp), lineHeight = 16.sp)
    }
}

@Composable
private fun PendingActionContent(
    lastRecognized: String,
    plannedAction: AssistantAction?,
    waitingState: AssistantState.WaitingForConfirmation?,
    viewModel: MainViewModel
) {
    AnimatedVisibility(visible = plannedAction != null) {
        ActionCard(lastRecognized = lastRecognized, plannedAction = plannedAction)
    }
    AnimatedVisibility(visible = waitingState != null) {
        waitingState?.let {
            ConfirmationDialog(
                pendingAction = it.pendingAction,
                prompt = it.prompt,
                onConfirm = { viewModel.confirmPendingAction() },
                onCancel = { viewModel.rejectPendingAction() }
            )
        }
    }
}

@Composable
private fun ConversationPanel(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(Panel.copy(alpha = 0.82f), RoundedCornerShape(24.dp))
            .border(1.dp, Border, RoundedCornerShape(24.dp)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CONVERSATION", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.weight(1f))
            Text(messages.size.toString() + " messages", color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Cyan.copy(alpha = 0.65f), modifier = Modifier.size(32.dp))
                    Text("Your conversation will appear here.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                ChatTranscript(messages = messages)
            }
        }
    }
}

@Composable
private fun QuickActions(viewModel: MainViewModel) {
    val suggestions = listOf("What time is it?", "Today's date", "Set a timer for 5 minutes", "Open Camera", "wifi settings open pannu", "time enna?")
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { suggestion ->
            AssistChip(
                onClick = {
                    viewModel.onTextInputChange(suggestion)
                    viewModel.submitTextInput()
                },
                label = { Text(suggestion, fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF17243A), labelColor = Color(0xFFD8E7FF))
            )
        }
    }
}

@Composable
private fun CommandInput(textInput: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = textInput,
            onValueChange = onValueChange,
            placeholder = { Text("Ask or type a command…", fontSize = 13.sp, color = Muted) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
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
        Surface(
            onClick = onSend,
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
}

@Composable
private fun WakeWordControl(
    isServiceRunning: Boolean,
    hasRecordAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF111D31)), shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Hey Jarvis detector (pending)", color = Color(0xFFD8E7FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (isServiceRunning) "Lifecycle demo running • no wake-word model or microphone capture"
                    else "Detection paused until an approved model is integrated",
                    color = Muted, fontSize = 9.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 3.dp)
                )
            }
            Switch(
                checked = isServiceRunning,
                onCheckedChange = { enabled ->
                    if (enabled && !hasRecordAudioPermission) onRequestPermission() else onToggle(enabled)
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Cyan)
            )
        }
    }
}

@Composable
private fun VoiceOrb(state: AssistantState, diameter: Dp) {
    val active = state is AssistantState.Listening || state is AssistantState.Processing || state is AssistantState.Speaking
    val transition = rememberInfiniteTransition(label = "jarvis-orb")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = if (active) 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "orb-pulse"
    )

    Box(modifier = Modifier.fillMaxWidth().height(diameter + 18.dp).padding(top = 4.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.size(diameter).scale(pulse)
                .background(Brush.radialGradient(listOf(Cyan.copy(alpha = 0.18f), Violet.copy(alpha = 0.10f), Color.Transparent)), CircleShape)
                .border(1.dp, Cyan.copy(alpha = if (active) 0.45f else 0.20f), CircleShape)
        )
        Box(
            modifier = Modifier.size(diameter * 0.72f)
                .background(Brush.radialGradient(listOf(Color(0xFF243D65), Color(0xFF14223D), Color(0xFF0B1222))), CircleShape)
                .border(1.dp, Violet.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.GraphicEq, contentDescription = "Assistant voice activity", tint = Cyan, modifier = Modifier.size(diameter * 0.34f))
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).size(6.dp).background(stateColor(state), CircleShape))
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

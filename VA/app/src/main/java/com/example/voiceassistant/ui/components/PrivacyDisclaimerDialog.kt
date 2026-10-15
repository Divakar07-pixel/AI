package com.example.voiceassistant.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voiceassistant.ui.theme.AssistantPrimary
import com.example.voiceassistant.ui.theme.SuccessGreen

@Composable
fun PrivacyDisclaimerDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = AssistantPrimary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Privacy & Safety Guarantees",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                PrivacyPoint(
                    icon = Icons.Default.MicNone,
                    title = "Audio Handling",
                    body = "This app does not write microphone audio to files. Android's selected speech-recognition service processes speech according to that provider's own settings and privacy policy."
                )

                Spacer(modifier = Modifier.height(12.dp))

                PrivacyPoint(
                    icon = Icons.Default.Lock,
                    title = "Optional AI Chat",
                    body = "Device commands are checked locally. When AI chat is configured, text messages and recent conversation history are sent to your configured Supabase function and AI provider. Do not share passwords, PINs, OTPs, or sensitive information. The provider API key stays on the server."
                )

                Spacer(modifier = Modifier.height(12.dp))

                PrivacyPoint(
                    icon = Icons.Default.VerifiedUser,
                    title = "Mandatory Confirmation for Sensitive Actions",
                    body = "Sensitive commands require confirmation before the app opens a system messaging app or dialer. You must review and complete the final send or call in Android."
                )

                Spacer(modifier = Modifier.height(12.dp))

                PrivacyPoint(
                    icon = Icons.Default.Security,
                    title = "Android Sandbox & Permissions",
                    body = "The assistant does not execute arbitrary shell commands, tap other apps secretly, or bypass Android OS permissions."
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Got it")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun PrivacyPoint(icon: ImageVector, title: String, body: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SuccessGreen,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
        }
    }
}

package com.example.voiceassistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voiceassistant.domain.model.AssistantAction
import com.example.voiceassistant.ui.theme.AssistantPrimary
import com.example.voiceassistant.ui.theme.SuccessGreen
import com.example.voiceassistant.ui.theme.WarningAmber

@Composable
fun ActionCard(
    lastRecognized: String,
    plannedAction: AssistantAction?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = lastRecognized.isNotBlank() || plannedAction != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (lastRecognized.isNotBlank()) {
                    Text(
                        text = "Recognized Command",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "\"$lastRecognized\"",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (plannedAction != null) {
                    val (icon, title, desc, tint) = getActionDetails(plannedAction)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(tint.copy(alpha = 0.12f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = tint
                            )
                            Text(
                                text = desc,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getActionDetails(action: AssistantAction): Quadruple<ImageVector, String, String, androidx.compose.ui.graphics.Color> {
    return when (action) {
        is AssistantAction.TellTime -> Quadruple(
            Icons.Default.Schedule,
            "Tell Time",
            action.formattedTime,
            AssistantPrimary
        )
        is AssistantAction.TellDate -> Quadruple(
            Icons.Default.DateRange,
            "Tell Date",
            action.formattedDate,
            AssistantPrimary
        )
        is AssistantAction.SetTimer -> Quadruple(
            Icons.Default.HourglassTop,
            "Set Timer",
            "${action.durationSeconds} seconds (${action.label})",
            SuccessGreen
        )
        is AssistantAction.SetAlarm -> Quadruple(
            Icons.Default.Alarm,
            "Set Alarm",
            String.format("%02d:%02d (%s)", action.hour, action.minute, action.label),
            SuccessGreen
        )
        is AssistantAction.OpenApp -> Quadruple(
            Icons.Default.Apps,
            "Open Application",
            "Launching ${action.appName}",
            AssistantPrimary
        )
        is AssistantAction.OpenSetting -> Quadruple(
            Icons.Default.Settings,
            "Open Settings",
            action.displayName,
            AssistantPrimary
        )
        is AssistantAction.LockDevice -> Quadruple(
            Icons.Default.Security,
            "Lock Device",
            "Lock the phone after device-admin permission is enabled",
            WarningAmber
        )
        is AssistantAction.RequireConfirmation -> Quadruple(
            Icons.Default.Warning,
            "Confirmation Required",
            action.explanation,
            WarningAmber
        )
        is AssistantAction.Unknown -> Quadruple(
            Icons.Default.Security,
            "Restricted / Unknown",
            action.reason,
            androidx.compose.ui.graphics.Color.Gray
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

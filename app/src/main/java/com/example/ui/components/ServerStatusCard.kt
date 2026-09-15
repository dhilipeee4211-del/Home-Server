package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ServerStatus
import com.example.ui.theme.AppTypography
import com.example.ui.theme.StatusRunning
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.brandGlow

@Composable
fun ServerStatusCard(
    serverStatus: ServerStatus,
    modifier: Modifier = Modifier
) {
    val stateColor = if (serverStatus.isOnline) StatusRunning else StatusWarning

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("server_status_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left status rail — reads as an equipment-panel indicator strip
            // rather than a rounded status pill.
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(stateColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "SERVER",
                            style = AppTypography.readoutLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = serverStatus.serverHostname.ifBlank { "Not configured" },
                            style = AppTypography.statValueLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    PulseIndicator(isLive = serverStatus.isOnline, color = stateColor, label = serverStatus.statusText)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (serverStatus.isOnline)
                        "Connected on local network"
                    else
                        "Target unreachable — connect to local Wi-Fi and check Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardMetric(
                        label = "PORT",
                        value = "8080",
                        subValue = "HTTP service",
                        icon = Icons.Default.Dns,
                        progressFraction = if (serverStatus.isOnline) 1.0f else 0.0f,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardMetric(
                        label = "PROTOCOL",
                        value = "HTTP/1.1",
                        subValue = serverStatus.loadAverage,
                        icon = Icons.Default.Memory,
                        progressFraction = if (serverStatus.isOnline) 1.0f else 0.0f,
                        accentColor = stateColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardMetric(
                        label = "ROLE",
                        value = "Fileserver",
                        subValue = serverStatus.osVersion,
                        icon = Icons.Default.Storage,
                        progressFraction = if (serverStatus.isOnline) 1.0f else 0.0f,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardMetric(
                        label = "STATE",
                        value = if (serverStatus.isOnline) "Active" else "Offline",
                        subValue = serverStatus.statusText,
                        icon = Icons.Default.Schedule,
                        progressFraction = if (serverStatus.isOnline) 1.0f else 0.1f,
                        accentColor = stateColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (serverStatus.isOnline) Icons.Default.Dns else Icons.Default.WifiOff,
                            contentDescription = "Network",
                            tint = if (serverStatus.isOnline) stateColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Network",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = serverStatus.networkStatus,
                        style = AppTypography.statValue.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * A softly pulsing dot for the live/online state, with a radial glow behind
 * it — replaces the flat colored pill badge used elsewhere in the app.
 */
@Composable
private fun PulseIndicator(isLive: Boolean, color: androidx.compose.ui.graphics.Color, label: String) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = if (isLive) 0.4f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(horizontalAlignment = Alignment.End) {
        Box(contentAlignment = Alignment.Center) {
            if (isLive) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(brandGlow(color), CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color.copy(alpha = if (isLive) alpha else 1f), CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = AppTypography.readoutLabel,
            color = color
        )
    }
}

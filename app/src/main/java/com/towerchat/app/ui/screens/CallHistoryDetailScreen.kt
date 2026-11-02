package com.towerchat.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerchat.app.data.model.CallLogEntry
import com.towerchat.app.data.model.CallLogGroup
import com.towerchat.app.data.model.CallType
import com.towerchat.app.ui.components.GoogleAvatar
import com.towerchat.app.ui.theme.DarkBorder
import com.towerchat.app.ui.theme.GmBackground
import com.towerchat.app.ui.theme.GmOnPrimary
import com.towerchat.app.ui.theme.GmPrimary
import com.towerchat.app.ui.theme.GmSurface
import com.towerchat.app.ui.theme.GmSurfaceVariant
import com.towerchat.app.ui.theme.GmTextMuted
import com.towerchat.app.ui.theme.GmTextPrimary
import com.towerchat.app.ui.theme.GmTextSecondary
import com.towerchat.app.ui.theme.QuotaExhaustedColor
import com.towerchat.app.ui.theme.QuotaSafeColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallHistoryDetailScreen(
    group: CallLogGroup,
    historyEntries: List<CallLogEntry>,
    onBack: () -> Unit,
    onCallNumber: (String) -> Unit,
    onMessageNumber: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GmBackground)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GmSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GmTextPrimary
                )
            }
            Text(
                text = "Call details",
                color = GmTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Contact Header Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GoogleAvatar(identifier = group.number, size = 72.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = group.displayName,
                color = GmTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = group.number,
                color = GmTextSecondary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons Row: Call & Message
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onCallNumber(group.number) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF34A853),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Call", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onMessageNumber(group.number) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GmPrimary,
                        contentColor = GmOnPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message", fontWeight = FontWeight.Bold)
                }
            }
        }

        HorizontalDivider(color = DarkBorder, thickness = 1.dp)

        // Call History Section Header
        Text(
            text = "CALL HISTORY",
            color = GmTextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        // Historical Calls List
        val entriesToShow = if (historyEntries.isNotEmpty()) historyEntries else group.entries

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(entriesToShow, key = { it.id }) { entry ->
                CallHistoryItem(entry = entry)
                HorizontalDivider(color = DarkBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun CallHistoryItem(entry: CallLogEntry) {
    val dateTimeStr = remember(entry.timestamp) {
        SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(entry.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, tint, label) = when (entry.type) {
            CallType.INCOMING -> Triple(Icons.AutoMirrored.Filled.CallReceived, QuotaSafeColor, "Incoming call")
            CallType.OUTGOING -> Triple(Icons.AutoMirrored.Filled.CallMade, GmPrimary, "Outgoing call")
            CallType.MISSED -> Triple(Icons.AutoMirrored.Filled.CallMissed, QuotaExhaustedColor, "Missed call")
            CallType.REJECTED -> Triple(Icons.Default.Block, GmTextMuted, "Rejected call")
            CallType.BLOCKED -> Triple(Icons.Default.Block, GmTextMuted, "Blocked call")
            else -> Triple(Icons.Default.Call, GmTextMuted, "Call")
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(GmSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = GmTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dateTimeStr,
                color = GmTextSecondary,
                fontSize = 12.sp
            )
        }

        if (entry.durationSeconds > 0) {
            val mins = entry.durationSeconds / 60
            val secs = entry.durationSeconds % 60
            val durationLabel = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
            Text(
                text = durationLabel,
                color = GmTextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

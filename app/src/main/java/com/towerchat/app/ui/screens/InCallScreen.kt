package com.towerchat.app.ui.screens

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerchat.app.telephony.CallRepository
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
import kotlinx.coroutines.delay

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InCallScreen(
    onAddCall: () -> Unit
) {
    val activeCall by CallRepository.activeCall.collectAsState()
    val ringingCall by CallRepository.ringingCall.collectAsState()
    val holdingCall by CallRepository.holdingCall.collectAsState()
    val audioState by CallRepository.audioState.collectAsState()

    var showDtmfSheet by remember { mutableStateOf(false) }
    var showRejectSmsSheet by remember { mutableStateOf(false) }
    var showAudioRouteDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GmBackground)
    ) {
        when {
            // Case 1: Pure Incoming Call (No Active Call)
            ringingCall != null && activeCall == null -> {
                val call = ringingCall!!
                val number = remember(call) { CallRepository.getPhoneNumber(call) }
                val name = remember(call) { CallRepository.getCallerDisplayName(call) }

                IncomingCallView(
                    phoneNumber = number,
                    displayName = name,
                    onAnswer = { CallRepository.answerIncomingCall() },
                    onDecline = { CallRepository.declineIncomingCall() },
                    onRejectWithSms = { showRejectSmsSheet = true }
                )
            }

            // Case 2: Active Call (or Dialing / Holding)
            activeCall != null || holdingCall != null -> {
                val call = activeCall ?: holdingCall!!
                val number = remember(call) { CallRepository.getPhoneNumber(call) }
                val name = remember(call) { CallRepository.getCallerDisplayName(call) }
                val isCallActive = call.state == Call.STATE_ACTIVE
                val isCallHolding = call.state == Call.STATE_HOLDING
                val isCallDialing = call.state == Call.STATE_DIALING || call.state == Call.STATE_CONNECTING

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Call Waiting Banner (if second call is ringing)
                    if (ringingCall != null) {
                        val waitingCall = ringingCall!!
                        val waitingNumber = remember(waitingCall) { CallRepository.getPhoneNumber(waitingCall) }
                        val waitingName = remember(waitingCall) { CallRepository.getCallerDisplayName(waitingCall) }

                        CallWaitingBanner(
                            phoneNumber = waitingNumber,
                            displayName = waitingName,
                            onHoldAndAnswer = { CallRepository.answerWaitingHoldActive() },
                            onEndAndAnswer = { CallRepository.answerWaitingEndActive() },
                            onDecline = { CallRepository.declineWaiting() }
                        )
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // Contact Avatar
                    GoogleAvatar(
                        identifier = if (!name.isNullOrBlank()) name else number,
                        size = 100.dp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Contact Name / Number
                    Text(
                        text = if (!name.isNullOrBlank()) name else number,
                        color = GmTextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (!name.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = number,
                            color = GmTextSecondary,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Call Status or Duration
                    if (isCallActive) {
                        CallTimerText(call = call)
                    } else if (isCallHolding) {
                        Text(
                            text = "On Hold",
                            color = QuotaExhaustedColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (isCallDialing) {
                        Text(
                            text = "Calling...",
                            color = GmPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // In-Call Action Grid (Mute, Keypad, Speaker, Add Call, Hold, Merge/Swap)
                    InCallControlGrid(
                        audioState = audioState,
                        isHolding = isCallHolding,
                        hasSecondCall = holdingCall != null,
                        onToggleMute = { CallRepository.toggleMute() },
                        onToggleKeypad = { showDtmfSheet = !showDtmfSheet },
                        onOpenAudioRoute = { showAudioRouteDialog = true },
                        onToggleHold = { CallRepository.toggleHold() },
                        onAddCall = onAddCall,
                        onMerge = { CallRepository.mergeCalls() },
                        onSwap = { CallRepository.swapCalls() }
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    // Big Red End Call Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(QuotaExhaustedColor)
                            .clickable { CallRepository.disconnectActiveCall() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }

        // Quick Reply Reject-with-SMS Bottom Sheet
        if (showRejectSmsSheet) {
            RejectWithSmsSheet(
                onDismiss = { showRejectSmsSheet = false },
                onSend = { message ->
                    showRejectSmsSheet = false
                    CallRepository.rejectWithSms(message)
                }
            )
        }

        // DTMF Keypad Bottom Sheet
        if (showDtmfSheet) {
            DtmfKeypadSheet(
                onDismiss = { showDtmfSheet = false },
                onDigit = { digit -> CallRepository.playDtmf(digit) },
                onRelease = { CallRepository.stopDtmf() }
            )
        }

        // Audio Route Selector Dialog
        if (showAudioRouteDialog && audioState != null) {
            AudioRouteDialog(
                currentRoute = audioState!!.route,
                supportedRouteMask = audioState!!.supportedRouteMask,
                onSelectRoute = { route ->
                    showAudioRouteDialog = false
                    CallRepository.setAudioRoute(route)
                },
                onDismiss = { showAudioRouteDialog = false }
            )
        }
    }
}

@Composable
private fun IncomingCallView(
    phoneNumber: String,
    displayName: String?,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onRejectWithSms: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(72.dp))

        GoogleAvatar(
            identifier = if (!displayName.isNullOrBlank()) displayName else phoneNumber,
            size = 110.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (!displayName.isNullOrBlank()) displayName else phoneNumber,
            color = GmTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        if (!displayName.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = phoneNumber,
                color = GmTextSecondary,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Incoming cellular call",
            color = GmPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.weight(1f))

        // Quick Reply Button
        TextButton(
            onClick = onRejectWithSms,
            colors = ButtonDefaults.textButtonColors(contentColor = GmTextSecondary)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reply with message", fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Answer & Decline Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline Button (Red)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(QuotaExhaustedColor)
                        .clickable { onDecline() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Decline",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Decline", color = GmTextSecondary, fontSize = 13.sp)
            }

            // Answer Button (Green)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(QuotaSafeColor)
                        .clickable { onAnswer() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Answer",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Answer", color = GmTextSecondary, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(56.dp))
    }
}

@Composable
private fun CallWaitingBanner(
    phoneNumber: String,
    displayName: String?,
    onHoldAndAnswer: () -> Unit,
    onEndAndAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GmSurfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(QuotaSafeColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = QuotaSafeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Call Waiting: ${displayName ?: phoneNumber}",
                        color = GmTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Another incoming call",
                        color = GmTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onHoldAndAnswer,
                    colors = ButtonDefaults.buttonColors(containerColor = GmPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Text("Hold & Answer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onEndAndAnswer,
                    colors = ButtonDefaults.buttonColors(containerColor = QuotaSafeColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Text("End & Answer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onDecline,
                    colors = ButtonDefaults.buttonColors(containerColor = QuotaExhaustedColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(0.7f).height(36.dp)
                ) {
                    Text("Decline", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CallTimerText(call: Call) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(call) {
        val connectTime = call.details?.connectTimeMillis ?: System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            elapsedSeconds = if (connectTime > 0L) (now - connectTime) / 1000L else 0L
            delay(1000)
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val hours = minutes / 60

    val timeString = if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }

    Text(
        text = timeString,
        color = GmTextSecondary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun InCallControlGrid(
    audioState: CallAudioState?,
    isHolding: Boolean,
    hasSecondCall: Boolean,
    onToggleMute: () -> Unit,
    onToggleKeypad: () -> Unit,
    onOpenAudioRoute: () -> Unit,
    onToggleHold: () -> Unit,
    onAddCall: () -> Unit,
    onMerge: () -> Unit,
    onSwap: () -> Unit
) {
    val isMuted = audioState?.isMuted ?: false
    val currentRoute = audioState?.route ?: CallAudioState.ROUTE_EARPIECE

    val routeIcon = when (currentRoute) {
        CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
        CallAudioState.ROUTE_BLUETOOTH -> Icons.Default.Bluetooth
        CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Default.Headset
        else -> Icons.Default.Hearing
    }

    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Row 1: Mute, Keypad, Audio Route
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InCallCircleButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (isMuted) "Unmute" else "Mute",
                isActive = isMuted,
                onClick = onToggleMute
            )
            InCallCircleButton(
                icon = Icons.Default.Dialpad,
                label = "Keypad",
                isActive = false,
                onClick = onToggleKeypad
            )
            InCallCircleButton(
                icon = routeIcon,
                label = when (currentRoute) {
                    CallAudioState.ROUTE_SPEAKER -> "Speaker"
                    CallAudioState.ROUTE_BLUETOOTH -> "Bluetooth"
                    CallAudioState.ROUTE_WIRED_HEADSET -> "Headset"
                    else -> "Earpiece"
                },
                isActive = currentRoute == CallAudioState.ROUTE_SPEAKER,
                onClick = onOpenAudioRoute
            )
        }

        // Row 2: Hold, Add Call, Merge or Swap
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InCallCircleButton(
                icon = if (isHolding) Icons.Default.PlayArrow else Icons.Default.Pause,
                label = if (isHolding) "Resume" else "Hold",
                isActive = isHolding,
                onClick = onToggleHold
            )
            InCallCircleButton(
                icon = Icons.Default.Add,
                label = "Add call",
                isActive = false,
                onClick = onAddCall
            )
            if (hasSecondCall) {
                InCallCircleButton(
                    icon = Icons.Default.SwapCalls,
                    label = "Swap",
                    isActive = false,
                    onClick = onSwap
                )
            } else {
                InCallCircleButton(
                    icon = Icons.Default.Merge,
                    label = "Merge",
                    isActive = false,
                    onClick = onMerge
                )
            }
        }
    }
}

@Composable
private fun InCallCircleButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(if (isActive) GmPrimary else GmSurfaceVariant)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) GmOnPrimary else GmTextPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, color = GmTextSecondary, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RejectWithSmsSheet(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var customText by remember { mutableStateOf("") }

    val defaultTemplates = listOf(
        "Can't talk now. What's up?",
        "I'll call you right back.",
        "I'm in a meeting. Will call you later.",
        "On my way."
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GmSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Quick reply with plain SMS",
                color = GmTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))

            for (template in defaultTemplates) {
                Text(
                    text = template,
                    color = GmTextPrimary,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSend(template) }
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                )
                HorizontalDivider(color = DarkBorder)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = { Text("Write custom reply...", color = GmTextMuted, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GmPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = GmTextPrimary,
                        unfocusedTextColor = GmTextPrimary
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (customText.isNotBlank()) onSend(customText.trim())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GmPrimary),
                    enabled = customText.isNotBlank()
                ) {
                    Text("Send")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DtmfKeypadSheet(
    onDismiss: () -> Unit,
    onDigit: (Char) -> Unit,
    onRelease: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var enteredDigits by remember { mutableStateOf("") }

    val keypadButtons = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GmSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = enteredDigits.ifEmpty { "Touch tone dialpad" },
                color = GmTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            for (row in keypadButtons) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (digit in row) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(GmSurfaceVariant)
                                .clickable {
                                    val c = digit.first()
                                    enteredDigits += c
                                    onDigit(c)
                                    onRelease()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = digit,
                                color = GmTextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AudioRouteDialog(
    currentRoute: Int,
    supportedRouteMask: Int,
    onSelectRoute: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val routes = mutableListOf<Pair<Int, String>>()

    if (supportedRouteMask and CallAudioState.ROUTE_EARPIECE != 0) {
        routes.add(Pair(CallAudioState.ROUTE_EARPIECE, "Phone earpiece"))
    }
    if (supportedRouteMask and CallAudioState.ROUTE_SPEAKER != 0) {
        routes.add(Pair(CallAudioState.ROUTE_SPEAKER, "Speakerphone"))
    }
    if (supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0) {
        routes.add(Pair(CallAudioState.ROUTE_BLUETOOTH, "Bluetooth audio"))
    }
    if (supportedRouteMask and CallAudioState.ROUTE_WIRED_HEADSET != 0) {
        routes.add(Pair(CallAudioState.ROUTE_WIRED_HEADSET, "Wired headphones"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Audio output",
                color = GmTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                for ((routeCode, routeName) in routes) {
                    val isSelected = routeCode == currentRoute
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) GmPrimary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onSelectRoute(routeCode) }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = routeName,
                            color = if (isSelected) GmPrimary else GmTextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Text("✓", color = GmPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GmTextSecondary)
            }
        },
        containerColor = GmSurface
    )
}

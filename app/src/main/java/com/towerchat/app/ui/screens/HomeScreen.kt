package com.towerchat.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerchat.app.data.model.QuotaState
import com.towerchat.app.ui.ConversationSummary
import com.towerchat.app.ui.components.GoogleAvatar
import com.towerchat.app.ui.components.QuotaPill
import com.towerchat.app.ui.theme.DarkBorder
import com.towerchat.app.ui.theme.GmBackground
import com.towerchat.app.ui.theme.GmOnBackground
import com.towerchat.app.ui.theme.GmOnPrimary
import com.towerchat.app.ui.theme.GmPrimary
import com.towerchat.app.ui.theme.GmSurface
import com.towerchat.app.ui.theme.GmSurfaceVariant
import com.towerchat.app.ui.theme.GmTextMuted
import com.towerchat.app.ui.theme.GmTextPrimary
import com.towerchat.app.ui.theme.GmTextSecondary
import com.towerchat.app.ui.theme.QuotaExhaustedColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    quota: QuotaState,
    conversations: List<ConversationSummary>,
    priorityContacts: Set<String> = emptySet(),
    quotaResetCountdown: String = "",
    onOpenChat: (String) -> Unit,
    onPickContact: () -> Unit,
    onClearSession: () -> Unit,
    onSendEmergencyBroadcast: () -> Unit = {},
    onPanicWipe: () -> Unit = {},
    onDeleteConversation: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var showStartChatDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showEmergencyDialog by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<String?>(null) }
    var newRecipientInput by remember { mutableStateOf("") }

    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) {
            conversations
        } else {
            conversations.filter {
                it.recipientNumber.contains(searchQuery.trim(), ignoreCase = true) ||
                        it.lastMessage.body.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
    }

    // Delete Conversation Confirmation Dialog (Long Press Triggered)
    if (contactToDelete != null) {
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = QuotaExhaustedColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete Conversation?",
                        color = GmTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = "Delete all messages and chat history with ${contactToDelete}? All data will be immediately erased from RAM.",
                    color = GmTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = contactToDelete
                        contactToDelete = null
                        if (target != null) {
                            onDeleteConversation(target)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QuotaExhaustedColor,
                        contentColor = GmBackground
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text("Cancel", color = GmTextSecondary)
                }
            },
            containerColor = GmSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // New Conversation Dialog
    if (showStartChatDialog) {
        AlertDialog(
            onDismissRequest = {
                showStartChatDialog = false
                newRecipientInput = ""
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ChatBubble,
                        contentDescription = null,
                        tint = GmPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "New conversation",
                        color = GmTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Enter a mobile number to start an end-to-end encrypted cellular SMS session:",
                        color = GmTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newRecipientInput,
                            onValueChange = { newRecipientInput = it },
                            placeholder = { Text("Type name or phone number", color = GmTextMuted, fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (newRecipientInput.isNotBlank()) {
                                        val target = newRecipientInput.trim()
                                        showStartChatDialog = false
                                        newRecipientInput = ""
                                        onOpenChat(target)
                                    }
                                }
                            ),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GmPrimary,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = GmTextPrimary,
                                unfocusedTextColor = GmTextPrimary,
                                focusedContainerColor = GmSurfaceVariant,
                                unfocusedContainerColor = GmSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                showStartChatDialog = false
                                onPickContact()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(GmSurfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContactPhone,
                                contentDescription = "Pick Contact",
                                tint = GmPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newRecipientInput.isNotBlank()) {
                            val target = newRecipientInput.trim()
                            showStartChatDialog = false
                            newRecipientInput = ""
                            onOpenChat(target)
                        }
                    },
                    enabled = newRecipientInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GmPrimary,
                        contentColor = GmOnPrimary
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showStartChatDialog = false
                    newRecipientInput = ""
                }) {
                    Text("Cancel", color = GmTextSecondary)
                }
            },
            containerColor = GmSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Emergency SOS Confirmation Dialog
    if (showEmergencyDialog) {
        AlertDialog(
            onDismissRequest = { showEmergencyDialog = false },
            title = {
                Text(
                    text = "🚨 Broadcast Emergency SOS?",
                    color = QuotaExhaustedColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will immediately dispatch emergency distress SMS to all your priority emergency contacts via the cellular network.",
                    color = GmTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmergencyDialog = false
                        onSendEmergencyBroadcast()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QuotaExhaustedColor,
                        contentColor = GmBackground
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Broadcast Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyDialog = false }) {
                    Text("Cancel", color = GmTextSecondary)
                }
            },
            containerColor = GmSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Google-style Profile / Security Modal
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoogleAvatar(identifier = "Towerchat", size = 36.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Towerchat Security",
                            color = GmTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "RAM-Only Persistence",
                            color = GmTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = GmPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Zero Internet · Zero Disk Storage",
                            color = GmTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "All encryption keys and messages reside strictly in volatile memory. No communication data is written to disk or synced to any cloud.",
                        color = GmTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Daily SMS Quota: ${quota.usedSmsCount}/100 sent (${quota.remaining} remaining today)",
                        color = GmTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (quotaResetCountdown.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⏳ $quotaResetCountdown",
                            color = GmPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Emergency SOS Trigger Button
                    Button(
                        onClick = {
                            showProfileDialog = false
                            showEmergencyDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = QuotaExhaustedColor.copy(alpha = 0.2f),
                            contentColor = QuotaExhaustedColor
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🚨 Broadcast Emergency SOS", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showProfileDialog = false
                        onPanicWipe()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QuotaExhaustedColor,
                        contentColor = GmBackground
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Panic Wipe (Zero Storage)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Close", color = GmTextSecondary)
                }
            },
            containerColor = GmSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GmBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            Spacer(modifier = Modifier.height(12.dp))

            // Google Messages Top Search Capsule
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(GmSurfaceVariant)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = GmTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search conversations",
                                color = GmTextMuted,
                                fontSize = 15.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = GmTextPrimary,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(GmPrimary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Quota Badge Pill
                    QuotaPill(quota = quota)

                    Spacer(modifier = Modifier.width(8.dp))

                    // Google-style Profile Avatar (Purge Session / Security Info)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(GmPrimary)
                            .clickable { showProfileDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Security Status",
                            tint = GmOnPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Google Messages Big Header
            Text(
                text = "Messages",
                color = GmTextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 10.dp)
            )

            // Conversation List or Empty State
            if (filteredConversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(GmSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = GmTextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No conversations yet" else "No matches found",
                            color = GmTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isBlank()) {
                                "Tap 'Start chat' to begin an encrypted SMS conversation"
                            } else {
                                "Try searching for a different number or word"
                            },
                            color = GmTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredConversations, key = { it.recipientNumber }) { conv ->
                        ConversationListItem(
                            conversation = conv,
                            isPriority = priorityContacts.contains(conv.recipientNumber),
                            onClick = { onOpenChat(conv.recipientNumber) },
                            onLongClick = { contactToDelete = conv.recipientNumber }
                        )
                    }
                }
            }
        }

        // Google Messages Extended FAB: "Start chat"
        ExtendedFloatingActionButton(
            onClick = { showStartChatDialog = true },
            icon = {
                Icon(
                    imageVector = Icons.Default.ChatBubble,
                    contentDescription = null,
                    tint = GmOnPrimary,
                    modifier = Modifier.size(20.dp)
                )
            },
            text = {
                Text(
                    text = "Start chat",
                    color = GmOnPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            containerColor = GmPrimary,
            contentColor = GmOnPrimary,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationListItem(
    conversation: ConversationSummary,
    isPriority: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val timeFormatted = remember(conversation.lastMessage.timestamp) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(conversation.lastMessage.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading Google Avatar
        GoogleAvatar(
            identifier = conversation.recipientNumber,
            size = 50.dp
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Details Column
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.recipientNumber,
                        color = GmTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isPriority) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(GmPrimary.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "★ Priority",
                                color = GmPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = timeFormatted,
                    color = GmTextMuted,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (conversation.lastMessage.isOutgoing) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Sent",
                        tint = GmTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "You: ",
                        color = GmTextSecondary,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = conversation.lastMessage.body,
                    color = GmTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

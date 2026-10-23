package com.towerchat.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerchat.app.data.model.Message
import com.towerchat.app.data.model.QuotaState
import com.towerchat.app.telephony.QuotaCalculator
import com.towerchat.app.ui.components.GoogleAvatar
import com.towerchat.app.ui.components.MessageBubble
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
import com.towerchat.app.ui.theme.QuotaWarningColor

data class ChatDetailState(
    val recipientNumber: String,
    val messages: List<Message>,
    val inputText: String,
    val quota: QuotaState,
    val safetyNumber: String,
    val isKeyChanged: Boolean,
    val isTyping: Boolean = false,
    val isPriority: Boolean = false,
    val isBurnOnExit: Boolean = true,
    val isAppOnlyMode: Boolean = true,
    val templates: List<String> = emptyList()
)

data class ChatDetailActions(
    val onDismissKeyChange: () -> Unit = {},
    val onTogglePriority: () -> Unit = {},
    val onToggleBurnOnExit: () -> Unit = {},
    val onToggleAppOnlyMode: () -> Unit = {},
    val onToggleSaveMessage: (String) -> Unit = {},
    val onDeleteMessage: (String) -> Unit = {},
    val onDeleteConversation: () -> Unit = {},
    val onWipeChatNow: () -> Unit = {},
    val onInputTextChange: (String) -> Unit = {},
    val onSendMessage: () -> Unit = {},
    val onSendReaction: (emoji: String, timestamp: Long) -> Unit = { _, _ -> },
    val onSelectTemplate: (String) -> Unit = {},
    val onVoiceCall: () -> Unit = {},
    val onBack: () -> Unit = {}
)

@Composable
fun ChatDetailScreen(
    state: ChatDetailState,
    actions: ChatDetailActions
) {
    val listState = rememberLazyListState()
    var showSafetyDialog by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    BackHandler(onBack = actions.onBack)

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    if (showSafetyDialog) {
        ChatSafetyDialog(
            recipientNumber = state.recipientNumber,
            safetyNumber = state.safetyNumber,
            onDismiss = { showSafetyDialog = false }
        )
    }

    if (showTemplatesDialog) {
        ChatTemplatesDialog(
            templates = state.templates,
            onDismiss = { showTemplatesDialog = false },
            onSelect = {
                showTemplatesDialog = false
                actions.onSelectTemplate(it)
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = QuotaExhaustedColor,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Delete chat?",
                    color = GmTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Delete all messages and chat history with ${state.recipientNumber}? All data will be immediately erased from RAM.",
                    color = GmTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        actions.onDeleteConversation()
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
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = GmTextSecondary)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            ChatDetailTopBar(
                recipientNumber = state.recipientNumber,
                isPriority = state.isPriority,
                isTyping = state.isTyping,
                isBurnOnExit = state.isBurnOnExit,
                onBack = actions.onBack,
                onVoiceCall = actions.onVoiceCall,
                onVerifySafety = { showSafetyDialog = true },
                onTogglePriority = actions.onTogglePriority,
                onToggleBurnOnExit = actions.onToggleBurnOnExit,
                onDeleteClick = { showDeleteDialog = true },
                onContactLongClick = { showDeleteDialog = true },
                onWipeChatNow = actions.onWipeChatNow
            )

            ChatWarningsSection(
                isKeyChanged = state.isKeyChanged,
                onDismissKeyChange = actions.onDismissKeyChange,
                isPriority = state.isPriority,
                quota = state.quota
            )

            ChatMessagesSection(
                listState = listState,
                messages = state.messages,
                isBurnOnExit = state.isBurnOnExit,
                onSafetyClick = { showSafetyDialog = true },
                onSendReaction = actions.onSendReaction,
                onToggleSave = actions.onToggleSaveMessage,
                onDeleteMessage = actions.onDeleteMessage,
                onDeleteAllChats = { showDeleteDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            if (state.inputText.isNotBlank()) {
                val counterLabel = QuotaCalculator.getCharacterCountLabel(state.inputText)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "$counterLabel · ${state.quota.remaining} left",
                        color = GmTextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            ChatComposeBar(
                inputText = state.inputText,
                isQuotaExhausted = state.quota.isExhausted,
                isAppOnlyMode = state.isAppOnlyMode,
                onToggleAppOnlyMode = actions.onToggleAppOnlyMode,
                onInputTextChange = actions.onInputTextChange,
                onSendMessage = actions.onSendMessage,
                onOpenTemplates = { showTemplatesDialog = true }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatDetailTopBar(
    recipientNumber: String,
    isPriority: Boolean,
    isTyping: Boolean,
    isBurnOnExit: Boolean,
    onBack: () -> Unit,
    onVoiceCall: () -> Unit,
    onVerifySafety: () -> Unit,
    onTogglePriority: () -> Unit,
    onToggleBurnOnExit: () -> Unit,
    onDeleteClick: () -> Unit,
    onContactLongClick: () -> Unit,
    onWipeChatNow: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GmSurface)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = GmTextPrimary
            )
        }

        GoogleAvatar(
            identifier = recipientNumber,
            size = 40.dp
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onContactLongClick
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = recipientNumber,
                    color = GmTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isPriority) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(GmPrimary.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
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
            if (isTyping) {
                Text(
                    text = "typing...",
                    color = GmPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = QuotaSafeColor,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "End-to-end encrypted",
                        color = QuotaSafeColor,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        IconButton(onClick = onVoiceCall) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Cellular Call",
                tint = GmTextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        // Dedicated Delete Chat Button
        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Chat",
                tint = QuotaExhaustedColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = GmTextPrimary
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(GmSurface)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (isBurnOnExit) "🔥 Disable Burn on Exit" else "🔥 Enable Burn on Exit (Snapchat Mode)",
                            color = if (isBurnOnExit) Color(0xFFFBBF24) else GmTextPrimary
                        )
                    },
                    onClick = {
                        showMenu = false
                        onToggleBurnOnExit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("🗑️ Delete Chat / Contact", color = QuotaExhaustedColor) },
                    onClick = {
                        showMenu = false
                        onDeleteClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("💥 Wipe Unsaved Messages Now", color = QuotaExhaustedColor) },
                    onClick = {
                        showMenu = false
                        onWipeChatNow()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Verify Safety Number", color = GmTextPrimary) },
                    onClick = {
                        showMenu = false
                        onVerifySafety()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Call Contact", color = GmTextPrimary) },
                    onClick = {
                        showMenu = false
                        onVoiceCall()
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (isPriority) "Remove Priority Status" else "Mark as Priority Contact", color = GmTextPrimary) },
                    onClick = {
                        showMenu = false
                        onTogglePriority()
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatWarningsSection(
    isKeyChanged: Boolean,
    onDismissKeyChange: () -> Unit,
    isPriority: Boolean,
    quota: QuotaState
) {
    AnimatedVisibility(
        visible = isKeyChanged,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(QuotaWarningColor.copy(alpha = 0.2f))
                .border(1.dp, QuotaWarningColor)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alert",
                    tint = QuotaWarningColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Security key changed for this contact. Possible SIM swap or MITM. Verify safety number.",
                    color = QuotaWarningColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            IconButton(onClick = onDismissKeyChange, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = QuotaWarningColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    AnimatedVisibility(
        visible = isPriority && quota.remaining < 15 && !quota.isExhausted,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(QuotaWarningColor.copy(alpha = 0.2f))
                .border(1.dp, QuotaWarningColor)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Priority Warning",
                tint = QuotaWarningColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Priority Contact: Quota critically low (${quota.remaining} SMS remaining). Conserve for urgent messages.",
                color = QuotaWarningColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    AnimatedVisibility(
        visible = quota.isExhausted,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(QuotaExhaustedColor.copy(alpha = 0.2f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = QuotaExhaustedColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Daily 100 SMS quota reached. Outgoing messages blocked until tomorrow.",
                color = QuotaExhaustedColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ChatMessagesSection(
    listState: LazyListState,
    messages: List<Message>,
    isBurnOnExit: Boolean,
    onSafetyClick: () -> Unit,
    onSendReaction: (emoji: String, timestamp: Long) -> Unit,
    onToggleSave: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onDeleteAllChats: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(GmSurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Today",
                        color = GmTextMuted,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(GmSurfaceVariant.copy(alpha = 0.6f))
                        .clickable { onSafetyClick() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = QuotaSafeColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "End-to-end encrypted · Zero disk persistence",
                            color = GmTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                if (isBurnOnExit) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2E1903))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔥", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Snapchat Mode: Unsaved messages vanish when you tap Back",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        items(messages, key = { it.id }) { message ->
            MessageBubble(
                message = message,
                onReact = { emoji -> onSendReaction(emoji, message.timestamp) },
                onToggleSave = onToggleSave,
                onDeleteMessage = onDeleteMessage,
                onDeleteAllChats = onDeleteAllChats
            )
        }
    }
}

@Composable
private fun ChatComposeBar(
    inputText: String,
    isQuotaExhausted: Boolean,
    isAppOnlyMode: Boolean,
    onToggleAppOnlyMode: () -> Unit,
    onInputTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onOpenTemplates: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(GmSurfaceVariant)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenTemplates,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Quick Templates",
                        tint = GmPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                TextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    enabled = !isQuotaExhausted,
                    placeholder = {
                        Text(
                            text = if (isQuotaExhausted) {
                                "Quota exhausted"
                            } else if (isAppOnlyMode) {
                                "TowerChat (App-to-App only)..."
                            } else {
                                "Plain SMS (Only message sent)..."
                            },
                            color = GmTextMuted,
                            fontSize = 14.sp
                        )
                    },
                    textStyle = TextStyle(
                        color = GmTextPrimary,
                        fontSize = 15.sp
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = GmPrimary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() && !isQuotaExhausted) {
                                onSendMessage()
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )

                // WhatsApp-Style Mode Switch: App-Only (Port 8901) vs Normal SMS
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isAppOnlyMode) GmPrimary.copy(alpha = 0.2f) else GmSurface)
                        .border(
                            width = 1.dp,
                            color = if (isAppOnlyMode) GmPrimary else DarkBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onToggleAppOnlyMode() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (isAppOnlyMode) Icons.Default.Lock else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isAppOnlyMode) GmPrimary else GmTextMuted,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = if (isAppOnlyMode) "App Only" else "SMS",
                            color = if (isAppOnlyMode) GmPrimary else GmTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        val canSend = inputText.isNotBlank() && !isQuotaExhausted
        IconButton(
            onClick = onSendMessage,
            enabled = canSend,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (canSend) GmPrimary else GmSurfaceVariant)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (canSend) GmOnPrimary else GmTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ChatSafetyDialog(
    recipientNumber: String,
    safetyNumber: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = GmPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Verify Safety Number",
                    color = GmTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "To verify that your cellular messages with $recipientNumber are end-to-end encrypted and free from carrier interception or SIM swapping, compare this 12-digit number over a secure cellular call:",
                    color = GmTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GmBackground)
                        .border(1.dp, GmPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = safetyNumber,
                        color = GmPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GmPrimary,
                    contentColor = GmOnPrimary
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Verified")
            }
        },
        containerColor = GmSurface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun ChatTemplatesDialog(
    templates: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Quick Response Templates",
                color = GmTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.forEach { template ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GmSurfaceVariant)
                            .clickable { onSelect(template) }
                            .padding(12.dp)
                    ) {
                        Text(
                            text = template,
                            color = GmTextPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = GmPrimary)
            }
        },
        containerColor = GmSurface,
        shape = RoundedCornerShape(24.dp)
    )
}

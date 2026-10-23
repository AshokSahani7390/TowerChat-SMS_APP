package com.towerchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerchat.app.data.model.Message
import com.towerchat.app.data.model.MessageStatus
import com.towerchat.app.ui.theme.GmOnPrimaryContainer
import com.towerchat.app.ui.theme.GmOnSecondaryContainer
import com.towerchat.app.ui.theme.GmPrimary
import com.towerchat.app.ui.theme.GmPrimaryContainer
import com.towerchat.app.ui.theme.GmSecondaryContainer
import com.towerchat.app.ui.theme.GmTextMuted
import com.towerchat.app.ui.theme.QuotaExhaustedColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    onReact: ((String) -> Unit)? = null,
    onToggleSave: ((String) -> Unit)? = null,
    onDeleteMessage: ((String) -> Unit)? = null,
    onDeleteAllChats: (() -> Unit)? = null
) {
    val isOutgoing = message.isOutgoing
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start
    val bubbleColor = if (isOutgoing) GmPrimaryContainer else GmSecondaryContainer
    val textColor = if (isOutgoing) GmOnPrimaryContainer else GmOnSecondaryContainer

    var showReactionMenu by remember { mutableStateOf(false) }
    val quickEmojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

    // Exact Google Messages Bubble Geometry
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isOutgoing) 20.dp else 4.dp,
        bottomEnd = if (isOutgoing) 4.dp else 20.dp
    )

    val timeFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp),
        horizontalAlignment = alignment
    ) {
        val savedBorderModifier = if (message.isSaved) {
            Modifier.border(1.5.dp, androidx.compose.ui.graphics.Color(0xFFF59E0B), bubbleShape)
        } else Modifier

        Box {
            Box(
                modifier = Modifier
                    .widthIn(min = 72.dp, max = 310.dp)
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .then(savedBorderModifier)
                    .combinedClickable(
                        onClick = {
                            // Single click or double click can toggle reaction or save
                        },
                        onLongClick = { showReactionMenu = true }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    if (message.isSaved) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 3.dp)
                        ) {
                            Text(
                                text = "📌 Saved in Chat",
                                color = androidx.compose.ui.graphics.Color(0xFFFBBF24),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = message.body,
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (message.partsCount > 1) {
                            Text(
                                text = "${message.partsCount} SMS · ",
                                color = if (isOutgoing) GmOnPrimaryContainer.copy(alpha = 0.7f) else GmTextMuted,
                                fontSize = 10.sp
                            )
                        }

                        Text(
                            text = timeFormatted,
                            color = if (isOutgoing) GmOnPrimaryContainer.copy(alpha = 0.7f) else GmTextMuted,
                            fontSize = 10.sp
                        )

                        if (isOutgoing) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Encrypted",
                                tint = if (isOutgoing) GmOnPrimaryContainer.copy(alpha = 0.7f) else GmTextMuted,
                                modifier = Modifier.size(10.dp)
                            )

                            Spacer(modifier = Modifier.width(3.dp))
                            when (message.status) {
                                MessageStatus.QUEUED, MessageStatus.SENDING -> {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Sending",
                                        tint = GmOnPrimaryContainer.copy(alpha = 0.7f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                MessageStatus.SENT -> {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Sent",
                                        tint = GmPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                MessageStatus.DELIVERED -> {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = "Delivered",
                                        tint = GmTextMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                MessageStatus.READ -> {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = "Read",
                                        tint = GmPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                MessageStatus.FAILED -> {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Failed",
                                        tint = QuotaExhaustedColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Emoji Reaction Bar (Google Messages style)
            DropdownMenu(
                expanded = showReactionMenu,
                onDismissRequest = { showReactionMenu = false },
                modifier = Modifier
                    .background(com.towerchat.app.ui.theme.GmSurface)
                    .border(1.dp, com.towerchat.app.ui.theme.DarkBorder, RoundedCornerShape(24.dp))
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickEmojis.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        showReactionMenu = false
                                        onReact?.invoke(emoji)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (message.isSaved) "📌 Unsave Message" else "📌 Save in Chat (Keep on Exit)",
                                color = if (message.isSaved) androidx.compose.ui.graphics.Color(0xFFFBBF24) else com.towerchat.app.ui.theme.GmTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        onClick = {
                            showReactionMenu = false
                            onToggleSave?.invoke(message.id)
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "🗑️ Delete Message",
                                color = QuotaExhaustedColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        onClick = {
                            showReactionMenu = false
                            onDeleteMessage?.invoke(message.id)
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "🗑️ Delete All Chats",
                                color = QuotaExhaustedColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        onClick = {
                            showReactionMenu = false
                            onDeleteAllChats?.invoke()
                        }
                    )
                }
            }
        }

        // Reaction pill display at bottom of bubble
        if (message.reaction != null) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, start = if (isOutgoing) 0.dp else 8.dp, end = if (isOutgoing) 8.dp else 0.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GmSecondaryContainer)
                    .border(1.dp, com.towerchat.app.ui.theme.DarkBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(text = message.reaction, fontSize = 13.sp)
            }
        }

        if (message.status == MessageStatus.FAILED && message.errorMessage != null) {
            Text(
                text = message.errorMessage,
                color = QuotaExhaustedColor,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, end = 6.dp)
            )
        }
    }
}

package com.towerchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerchat.app.ui.theme.GmAvatarBlue
import com.towerchat.app.ui.theme.GmAvatarCyan
import com.towerchat.app.ui.theme.GmAvatarGreen
import com.towerchat.app.ui.theme.GmAvatarPurple
import com.towerchat.app.ui.theme.GmAvatarRed
import com.towerchat.app.ui.theme.GmAvatarYellow
import kotlin.math.abs

@Composable
fun GoogleAvatar(
    identifier: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val avatarColors = listOf(
        GmAvatarBlue,
        GmAvatarGreen,
        GmAvatarPurple,
        GmAvatarRed,
        GmAvatarYellow,
        GmAvatarCyan
    )

    val cleanId = identifier.trim().removePrefix("+").removePrefix("91")
    val initial = cleanId.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
    val colorIndex = abs(cleanId.hashCode()) % avatarColors.size
    val bgColor = avatarColors[colorIndex]

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp
        )
    }
}

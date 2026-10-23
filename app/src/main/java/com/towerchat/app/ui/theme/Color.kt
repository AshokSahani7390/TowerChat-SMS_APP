package com.towerchat.app.ui.theme

import androidx.compose.ui.graphics.Color

// Google Messages Material 3 Palette (Dark Mode)
val GmBackground = Color(0xFF131314)
val GmSurface = Color(0xFF1E1F20)
val GmSurfaceVariant = Color(0xFF282A2D)
val GmSurfaceContainer = Color(0xFF202225)
val GmSearchBar = Color(0xFF282A2D)

// Google Messages Signature Blue
val GmPrimary = Color(0xFFA8C7FA)
val GmOnPrimary = Color(0xFF042E5E)
val GmPrimaryContainer = Color(0xFF004A77) // Outgoing bubble background
val GmOnPrimaryContainer = Color(0xFFD3E3FD) // Outgoing bubble text

val GmSecondaryContainer = Color(0xFF282A2D) // Incoming bubble background
val GmOnSecondaryContainer = Color(0xFFE2E2E6) // Incoming bubble text

val GmOutline = Color(0xFF44474E)
val GmOutlineVariant = Color(0xFF33353A)

val GmTextPrimary = Color(0xFFE3E3E3)
val GmOnBackground = GmTextPrimary
val GmTextSecondary = Color(0xFF9095A0)
val GmOnSurfaceVariant = Color(0xFF9095A0)
val GmTextMuted = Color(0xFF757A84)

// Google Contact Avatar Palettes
val GmAvatarBlue = Color(0xFF1A73E8)
val GmAvatarGreen = Color(0xFF1E8E3E)
val GmAvatarYellow = Color(0xFFF9AB00)
val GmAvatarRed = Color(0xFFD93025)
val GmAvatarPurple = Color(0xFF9334E6)
val GmAvatarCyan = Color(0xFF007B83)

// Quota Colors
val QuotaSafeColor = Color(0xFF81C995)
val QuotaWarningColor = Color(0xFFFDD663)
val QuotaExhaustedColor = Color(0xFFF28B82)

// Compatibility aliases
val SignalCyan = GmPrimary
val SignalCyanDark = Color(0xFF0B57D0)
val DarkBackground = GmBackground
val DarkSurface = GmSurface
val DarkSurfaceVariant = GmSurfaceVariant
val DarkBorder = GmOutlineVariant
val TextPrimary = GmTextPrimary
val TextSecondary = GmTextSecondary
val TextMuted = GmTextMuted
val SentBubbleColor = GmPrimaryContainer
val SentBubbleText = GmOnPrimaryContainer
val ReceivedBubbleColor = GmSecondaryContainer
val ReceivedBubbleText = GmOnSecondaryContainer

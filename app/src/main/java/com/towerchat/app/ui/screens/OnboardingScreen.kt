package com.towerchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerchat.app.ui.theme.DarkBorder
import com.towerchat.app.ui.theme.GmBackground
import com.towerchat.app.ui.theme.GmOnPrimary
import com.towerchat.app.ui.theme.GmPrimary
import com.towerchat.app.ui.theme.GmSurface
import com.towerchat.app.ui.theme.GmSurfaceVariant
import com.towerchat.app.ui.theme.GmTextMuted
import com.towerchat.app.ui.theme.GmTextPrimary
import com.towerchat.app.ui.theme.GmTextSecondary
import com.towerchat.app.ui.theme.QuotaSafeColor
import com.towerchat.app.ui.theme.QuotaWarningColor

@Composable
fun OnboardingScreen(
    detectedNumber: String?,
    onComplete: (String) -> Unit
) {
    var manualNumber by remember { mutableStateOf(detectedNumber ?: "+91 98765 43210") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GmBackground)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Spacer(modifier = Modifier.height(32.dp))

                // App Brand Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(GmSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CellTower,
                            contentDescription = "Cell Tower",
                            tint = GmPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Towerchat",
                            color = GmTextPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Zero Internet · Cellular Native",
                            color = GmPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Privacy & Plaintext Disclosure Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GmSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .padding(18.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Plaintext Disclosure",
                                tint = QuotaWarningColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Carrier Cellular Notice",
                                color = GmTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Towerchat routes messages directly over carrier cellular channels (SMS/Voice). Cellular operators transmit encrypted payloads without cloud internet servers.",
                            color = GmTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "RAM Only",
                                tint = QuotaSafeColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Zero Disk Persistence: All encryption keys & chats live in RAM only and are wiped upon exit.",
                                color = GmTextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // SIM Phone Number Auto-Detect or Manual Field
                Text(
                    text = "YOUR PHONE NUMBER",
                    color = GmTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (!detectedNumber.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GmSurface)
                            .border(1.dp, GmPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Detected",
                            tint = GmPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = detectedNumber,
                                color = GmTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Auto-detected from active SIM",
                                color = GmTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = manualNumber,
                        onValueChange = { manualNumber = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. +91 98765 43210", color = GmTextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GmPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = GmTextPrimary,
                            unfocusedTextColor = GmTextPrimary,
                            focusedContainerColor = GmSurface,
                            unfocusedContainerColor = GmSurface
                        )
                    )
                    Text(
                        text = "SIM auto-detect returned null. Please enter your mobile number.",
                        color = GmTextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }

            // Enter Towerchat Button (Google Messages Pill Button)
            Button(
                onClick = {
                    val finalNumber = if (!detectedNumber.isNullOrBlank()) detectedNumber else manualNumber
                    if (finalNumber.isNotBlank()) {
                        onComplete(finalNumber)
                    }
                },
                enabled = (!detectedNumber.isNullOrBlank() || manualNumber.isNotBlank()),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GmPrimary,
                    contentColor = GmOnPrimary
                )
            ) {
                Text(
                    text = "Get Started",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

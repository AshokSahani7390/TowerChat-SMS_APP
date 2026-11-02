package com.towerchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerchat.app.data.model.SimAccountInfo
import com.towerchat.app.ui.theme.DarkBorder
import com.towerchat.app.ui.theme.GmPrimary
import com.towerchat.app.ui.theme.GmSurface
import com.towerchat.app.ui.theme.GmTextPrimary
import com.towerchat.app.ui.theme.GmTextSecondary

@Composable
fun SimChoiceDialog(
    phoneNumber: String,
    simAccounts: List<SimAccountInfo>,
    onSelectSim: (SimAccountInfo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Call via",
                    color = GmTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = phoneNumber,
                    color = GmTextSecondary,
                    fontSize = 14.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                for (sim in simAccounts) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectSim(sim) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GmPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SimCard,
                                contentDescription = null,
                                tint = GmPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "SIM ${sim.slotIndex + 1}: ${sim.carrierName}",
                                color = GmTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!sim.number.isNullOrBlank()) {
                                Text(
                                    text = sim.number,
                                    color = GmTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GmTextSecondary)
            }
        },
        containerColor = GmSurface,
        shape = RoundedCornerShape(20.dp)
    )
}
